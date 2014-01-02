Redis Session Manager for Apache Tomcat
=======================================

Overview
--------

An session manager implementation that stores sessions in Redis for easy distribution of requests across a cluster of Tomcat servers. Sessions are implemented as as non-sticky--that is, each request is able to go to any server in the cluster (unlike the Apache provided Tomcat clustering setup.)

Sessions are stored into Redis immediately upon creation for use by other servers. Sessions are loaded as requested directly from Redis (but subsequent requests for the session during the same request context will return a ThreadLocal cache rather than hitting Redis multiple times.) In order to prevent collisions (and lost writes) as much as possible, session data is only updated in Redis if the session has been modified.

The manager relies on the native expiration capability of Redis to expire keys for automatic session expiration to avoid the overhead of constantly searching the entire list of sessions for expired sessions.

Data stored in the session must be Serializable.

Tomcat Versions
---------------

This project supports both Tomcat 6 and Tomcat 7. Starting at project version 1.1, precompiled JAR downloads are available for either version of Tomcat while project versions before 1.1 are only available for Tomcat 6.

The official release branches in Git are as follows:
* `tomcat-6`: Continuing work for Tomcat 6 releases. Compatible with Java 6.
* `master`: Continuing work for Tomcat 7 releases. Compatible with Java 6 or 7.
* `tomcat-7`: Finalized; has now been merged into `master`. Compatible with Java 6 or 7.
* `java-7`: All of the work from master for Tomcat 7 but taking advantage of new features in Java 7. Compatible with Java 7 only.

Architecture
------------

* RedisSessionManager: provides the session creation, saving, and loading functionality.
* RedisSessionHandlerValve: ensures that sessions are saved after a request is finished processing.

Note: this architecture differs from the Apache PersistentManager implementation which implements persistent sticky sessions. Because that implementation expects all requests from a specific session to be routed to the same server, the timing persistence of sessions is non-deterministic since it is primarily for failover capabilities.

Usage
-----

Add the following into your Tomcat context.xml (or the context block of the server.xml if applicable.)

    <Valve className="blackboard.catalina.session.RedisSessionHandlerValve" />
    <Manager className="blackboard.catalina.session.RedisSessionManager"
             host="localhost" <!-- optional: defaults to "localhost" -->
             port="6379" <!-- optional: defaults to "6379" -->
             database="0" <!-- optional: defaults to "0" -->
             maxInactiveInterval="60" <!-- optional: defaults to "60" (in seconds) --> />

The Valve must be declared before the Manager.

Copy the tomcat-redis-session-manager.jar and jedis-2.0.0.jar files into the `lib` directory of your Tomcat installation.

Reboot the server, and sessions should now be stored in Redis.

Possible Issues
---------------

There is the possibility of a race condition that would cause seeming invisibility of the session immediately after your web application logs in a user: if the response has finished streaming and the client requests a new page before the valve has been able to complete saving the session into Redis, then the new request will not see the session.

This condition will be detected by the session manager and a java.lang.IllegalStateException with the message `Race condition encountered: attempted to load session[SESSION_ID] which has been created but not yet serialized.` will be thrown.

Normally this should be incredibly unlikely (insert joke about programmers and "this should never happen" statements here) since the connection to save the session into Redis is almost guaranteed to be faster than the latency between a client receiving the response, processing it, and starting a new request.

If you encounter errors, then you can force save the session early (before sending a response to the client) then you can retrieve the current session, and call `currentSession.manager.save(currentSession)` to synchronously eliminate the race condition. Note: this will only work directly if your application has the actual session object directly exposed. Many frameworks (and often even Tomcat) will expose the session in their own wrapper HttpSession implementing class. You may be able to dig through these layers to expose the actual underlying RedisSession instance--if so, then using that instance will allow you to implement the workaround.

Acknowledgements
----------------

This project is based on the Redis session manager developed by jcoleman, https://github.com/jcoleman/tomcat-redis-session-manager (originally forked from there), but has gone through significant refactoring, including a package name change.

The architecture of this project was based on the Mongo-Tomcat-Sessions project found at https://github.com/dawsonsystems/Mongo-Tomcat-Sessions
