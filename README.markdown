Redis Session Manager for Apache Tomcat
=======================================

Overview
--------

An session manager implementation that stores sessions in Redis for easy distribution of requests across a cluster of Tomcat servers. Sessions are implemented as as non-sticky--that is, each request is able to go to any server in the cluster (unlike the Apache provided Tomcat clustering setup.)

Sessions are stored into Redis immediately upon creation for use by other servers. Sessions are loaded as requested directly from Redis (but subsequent requests for the session during the same request context will return a ThreadLocal cache rather than hitting Redis multiple times.) In order to prevent collisions (and lost writes) as much as possible, session data is only updated in Redis if the session has been modified.

The manager relies on the native expiration capability of Redis to expire keys for automatic session expiration to avoid the overhead of constantly searching the entire list of sessions for expired sessions.

Data stored in the session must be Serializable.


Support this project!
---------------------

This is an open-source project. Currently I'm not using this for anything personal or professional, so I'm not able to commit much time to the project, though I attempt to merge in reasonable pull requests. If you like to support further development of this project, you can donate via Pledgie:

<a href='https://pledgie.com/campaigns/26802'><img alt='Click here to lend your support to: Tomcat Redis Session Manager and make a donation at pledgie.com !' src='https://pledgie.com/campaigns/26802.png?skin_name=chrome' border='0' ></a>


Commercial Support
------------------

If your business depends on Tomcat and persistent sessions and you need a specific feature this project doesn't yet support, a quick bug fix you don't have time to author, or commercial support when things go wrong, I can provide support on a contractual support through my consultancy, Orange Function, LLC. If you have any questions or would like to begin discussing a deal, please contact me via email at james@orangefunction.com.


Tomcat Versions
---------------

This project supports both Tomcat 6 and Tomcat 7. Starting at project version 1.1, precompiled JAR downloads are available for either version of Tomcat while project versions before 1.1 are only available for Tomcat 6.

The official release branches in Git are as follows:
* `master`: Continuing work for Tomcat 7 releases. Compatible with Java 7.
* `tomcat-6`: Deprecated; may accept submitted patches, but no new work is being done on this branch. Compatible with Tomcat 6 and Java 6.

Finalized branches include:
* `tomcat-7`: Has been merged into `master`. Compatible with Java 6 or 7.
* `java-7`: Has been merged into `master`. All of the work from master for Tomcat 7 but taking advantage of new features in Java 7. Compatible with Java 7 only.

Tomcat 8
--------

Tomcat 8 is not currently supported and has not been tested or developed for at all In fact, as noted in various bug reports, the project currently fails to compile when linked with Tomcat 8.

I currently don't have the time to add Tomcat 8 support in my spare time. However if you're interested in Tomcat 8 support for your particular use case and/or business, as the README notes, I'm available as a consultancy on a contractual basis. If you'd like to pursue getting this feature added at a contract rate (and gain some commercial support as well), feel free to contact me at james@orangefunction.com.

Architecture
------------

* RedisSessionManager: provides the session creation, saving, and loading functionality.
* RedisSessionHandlerValve: ensures that sessions are saved after a request is finished processing.

Note: this architecture differs from the Apache PersistentManager implementation which implements persistent sticky sessions. Because that implementation expects all requests from a specific session to be routed to the same server, the timing persistence of sessions is non-deterministic since it is primarily for failover capabilities.

Usage
-----

Add the following into your Tomcat context.xml (or the context block of the server.xml if applicable.)

    <Valve className="com.orangefunction.tomcat.redissessions.RedisSessionHandlerValve" />
    <Manager className="com.orangefunction.tomcat.redissessions.RedisSessionManager"
             host="localhost" <!-- optional: defaults to "localhost" -->
             port="6379" <!-- optional: defaults to "6379" -->
             database="0" <!-- optional: defaults to "0" -->
             maxInactiveInterval="60" <!-- optional: defaults to "60" (in seconds) -->
             sessionPersistPolicies="PERSIST_POLICY_1,PERSIST_POLICY_2,.." <!-- optional -->
             sentinelMaster="SentinelMasterName" <!-- optional -->
             sentinels="sentinel-host-1:port,sentinel-host-2:port,.." <!-- optional --> />

The Valve must be declared before the Manager.

Copy the following files into the `TOMCAT_BASE/lib` directory:

* tomcat-redis-session-manager-VERSION.jar
* jedis-2.5.2.jar
* commons-pool2-2.2.jar

Reboot the server, and sessions should now be stored in Redis.

Connection Pool Configuration
-----------------------------

All of the configuration options from both `org.apache.commons.pool2.impl.GenericObjectPoolConfig` and `org.apache.commons.pool2.impl.BaseObjectPoolConfig` are also configurable for the Redis connection pool used by the session manager. To configure any of these attributes (e.g., `maxIdle` and `testOnBorrow`) just use the config attribute name prefixed with `connectionPool` (e.g., `connectionPoolMaxIdle` and `connectionPoolTestOnBorrow`) and set the desired value in the `<Manager>` declaration in your Tomcat context.xml.

Session Change Tracking
-----------------------

As noted in the "Overview" section above, in order to prevent colliding writes, the Redis Session Manager only serializes the session object into Redis if the session object has changed (it always updates the expiration separately however.) This dirty tracking marks the session as needing serialization according to the following rules:

* Calling `session.removeAttribute(key)` always marks the session as dirty (needing serialization.)
* Calling `session.setAttribute(key, newAttributeValue)` marks the session as dirty if any of the following are true:
    * `previousAttributeValue == null && newAttributeValue != null`
    * `previousAttributeValue != null && newAttributeValue == null`
    * `!newAttributeValue.getClass().isInstance(previousAttributeValue)`
    * `!newAttributeValue.equals(previousAttributeValue)`

This feature can have the unintended consequence of hiding writes if you implicitly change a key in the session or if the object's equality does not change even though the key is updated. For example, assuming the session already contains the key `"myArray"` with an Array instance as its corresponding value, and has been previously serialized, the following code would not cause the session to be serialized again:

    List myArray = session.getAttribute("myArray");
    myArray.add(additionalArrayValue);

If your code makes these kind of changes, then the RedisSession provides a mechanism by which you can mark the session as dirty in order to guarantee serialization at the end of the request. For example:

    List myArray = session.getAttribute("myArray");
    myArray.add(additionalArrayValue);
    session.setAttribute("__changed__");

In order to not cause issues with an application that may already use the key `"__changed__"`, this feature is disabled by default. To enable this feature, simple call the following code in your application's initialization:

    RedisSession.setManualDirtyTrackingSupportEnabled(true);

This feature also allows the attribute key used to mark the session as dirty to be changed. For example, if you executed the following:

    RedisSession.setManualDirtyTrackingAttributeKey("customDirtyFlag");

Then the example above would look like this:

    List myArray = session.getAttribute("myArray");
    myArray.add(additionalArrayValue);
    session.setAttribute("customDirtyFlag");

Persistence Policies
--------------------

With an persistent session storage there is going to be the distinct possibility of race conditions when requests for the same session overlap/occur concurrently. Additionally, because the session manager works by serializing the entire session object into Redis, concurrent updating of the session will exhibit last-write-wins behavior for the entire session (not just specific session attributes).

Since each situation is different, the manager gives you several options which control the details of when/how sessions are persisted. Each of the following options may be selected by setting the `sessionPersistPolicies="PERSIST_POLICY_1,PERSIST_POLICY_2,.."` attributes in your manager declaration in Tomcat's context.xml. Unless noted otherwise, the various options are all combinable.

- `SAVE_ON_CHANGE`: every time `session.setAttribute()` or `session.removeAttribute()` is called the session will be saved. __Note:__ This feature cannot detect changes made to objects already stored in a specific session attribute. __Tradeoffs__: This option will degrade performance slightly as any change to the session will save the session synchronously to Redis.
- `ALWAYS_SAVE_AFTER_REQUEST`: force saving after every request, regardless of whether or not the manager has detected changes to the session. This option is particularly useful if you make changes to objects already stored in a specific session attribute. __Tradeoff:__ This option make actually increase the liklihood of race conditions if not all of your requests change the session.


Testing/Example App
-------------------

For full integration testing as well as a demonstration of how to use the session manager, this project contains an example app and a virtual server setup via Vagrant and Chef.

To get the example server up and running, you'll need to do the following:
1. Download and install Virtual Box (4.3.12 at the time of this writing) from https://www.virtualbox.org/wiki/Downloads
1. Download and install the latest version (1.6.3 at the time of this writing) of Vagrant from http://www.vagrantup.com/downloads.html
1. Install Ruby, if necessary.
1. Install Berkshelf with `gem install berkshelf`
1. Install the Vagrant Berkshelf plugin with `vagrant plugin install vagrant-berkshelf --plugin-version '>= 2.0.1'`
1. Install the Vagrant Cachier plugin for _speed_ with `vagrant plugin install vagrant-cachier`
1. Install the Vagrant Omnibus plugin with `vagrant plugin install vagrant-omnibus`
1. Install the required Ruby gems with `PROJECT_ROOT/bundle install`
1. Boot the virtual machine with `PROJECT_ROOT/vagrant up`
1. Run the tests with `PROJECT_ROOT/rspec`


Acknowledgements
----------------

The architecture of this project was based on the Mongo-Tomcat-Sessions project found at https://github.com/dawsonsystems/Mongo-Tomcat-Sessions