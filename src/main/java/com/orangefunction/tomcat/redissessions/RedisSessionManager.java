package com.orangefunction.tomcat.redissessions;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Valve;
import org.apache.catalina.Session;
import org.apache.catalina.session.ManagerBase;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.commons.pool2.impl.BaseObjectPoolConfig;

import redis.clients.util.Pool;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


public class RedisSessionManager extends ManagerBase implements Lifecycle {

  enum SessionPersistPolicy {
    DEFAULT,
    SAVE_ON_CHANGE,
    ALWAYS_SAVE_AFTER_REQUEST;

    static SessionPersistPolicy fromName(String name) {
      for (SessionPersistPolicy policy : SessionPersistPolicy.values()) {
        if (policy.name().equalsIgnoreCase(name)) {
          return policy;
        }
      }
      throw new IllegalArgumentException("Invalid session persist policy [" + name + "]. Must be one of " + Arrays.asList(SessionPersistPolicy.values())+ ".");
    }
  }

  protected byte[] NULL_SESSION = "null".getBytes();

  private final Log log = LogFactory.getLog(RedisSessionManager.class);

  protected String host = "localhost";
  protected int port = 6379;
  protected int database = 0;
  protected String password = null;
  protected int timeout = Protocol.DEFAULT_TIMEOUT;
  protected String sentinelMaster = null;
  Set<String> sentinelSet = null;

  protected Pool<Jedis> connectionPool;
  protected JedisPoolConfig connectionPoolConfig = new JedisPoolConfig();

  protected RedisSessionHandlerValve handlerValve;
  protected ThreadLocal<RedisSession> currentSession = new ThreadLocal<>();
  protected ThreadLocal<SessionSerializationMetadata> currentSessionSerializationMetadata = new ThreadLocal<>();
  protected ThreadLocal<String> currentSessionId = new ThreadLocal<>();
  protected ThreadLocal<Boolean> currentSessionIsPersisted = new ThreadLocal<>();
  protected Serializer serializer;

  protected static String name = "RedisSessionManager";

  protected String serializationStrategyClass = "com.orangefunction.tomcat.redissessions.JavaSerializer";

  protected EnumSet<SessionPersistPolicy> sessionPersistPoliciesSet = EnumSet.of(SessionPersistPolicy.DEFAULT);

  /**
   * The lifecycle event support for this component.
   */
  protected LifecycleSupport lifecycle = new LifecycleSupport(this);

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public int getDatabase() {
    return database;
  }

  public void setDatabase(int database) {
    this.database = database;
  }

  public int getTimeout() {
    return timeout;
  }

  public void setTimeout(int timeout) {
    this.timeout = timeout;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public void setSerializationStrategyClass(String strategy) {
    this.serializationStrategyClass = strategy;
  }

  public String getSessionPersistPolicies() {
    StringBuilder policies = new StringBuilder();
    for (Iterator<SessionPersistPolicy> iter = this.sessionPersistPoliciesSet.iterator(); iter.hasNext();) {
      SessionPersistPolicy policy = iter.next();
      policies.append(policy.name());
      if (iter.hasNext()) {
        policies.append(",");
      }
    }
    return policies.toString();
  }

  public void setSessionPersistPolicies(String sessionPersistPolicies) {
    String[] policyArray = sessionPersistPolicies.split(",");
    EnumSet<SessionPersistPolicy> policySet = EnumSet.of(SessionPersistPolicy.DEFAULT);
    for (String policyName : policyArray) {
      SessionPersistPolicy policy = SessionPersistPolicy.fromName(policyName);
      policySet.add(policy);
    }
    this.sessionPersistPoliciesSet = policySet;
  }

  public boolean getSaveOnChange() {
    return this.sessionPersistPoliciesSet.contains(SessionPersistPolicy.SAVE_ON_CHANGE);
  }

  public boolean getAlwaysSaveAfterRequest() {
    return this.sessionPersistPoliciesSet.contains(SessionPersistPolicy.ALWAYS_SAVE_AFTER_REQUEST);
  }

  public String getSentinels() {
    StringBuilder sentinels = new StringBuilder();
    for (Iterator<String> iter = this.sentinelSet.iterator(); iter.hasNext();) {
      sentinels.append(iter.next());
      if (iter.hasNext()) {
        sentinels.append(",");
      }
    }
    return sentinels.toString();
  }

  public void setSentinels(String sentinels) {
    if (null == sentinels) {
      sentinels = "";
    }

    String[] sentinelArray = sentinels.split(",");
    this.sentinelSet = new HashSet<String>(Arrays.asList(sentinelArray));
  }

  public Set<String> getSentinelSet() {
    return this.sentinelSet;
  }

  public String getSentinelMaster() {
    return this.sentinelMaster;
  }

  public void setSentinelMaster(String master) {
    this.sentinelMaster = master;
  }

  @Override
  public int getRejectedSessions() {
    // Essentially do nothing.
    return 0;
  }

  public void setRejectedSessions(int i) {
    // Do nothing.
  }

  protected Jedis acquireConnection() {
    Jedis jedis = connectionPool.getResource();

    if (getDatabase() != 0) {
      jedis.select(getDatabase());
    }

    return jedis;
  }

  protected void returnConnection(Jedis jedis, Boolean error) {
    if (error) {
      connectionPool.returnBrokenResource(jedis);
    } else {
      connectionPool.returnResource(jedis);
    }
  }

  protected void returnConnection(Jedis jedis) {
    returnConnection(jedis, false);
  }

  @Override
  public void load() throws ClassNotFoundException, IOException {

  }

  @Override
  public void unload() throws IOException {

  }

  /**
   * Add a lifecycle event listener to this component.
   *
   * @param listener The listener to add
   */
  @Override
  public void addLifecycleListener(LifecycleListener listener) {
    lifecycle.addLifecycleListener(listener);
  }

  /**
   * Get the lifecycle listeners associated with this lifecycle. If this
   * Lifecycle has no listeners registered, a zero-length array is returned.
   */
  @Override
  public LifecycleListener[] findLifecycleListeners() {
    return lifecycle.findLifecycleListeners();
  }


  /**
   * Remove a lifecycle event listener from this component.
   *
   * @param listener The listener to remove
   */
  @Override
  public void removeLifecycleListener(LifecycleListener listener) {
    lifecycle.removeLifecycleListener(listener);
  }

  /**
   * Start this component and implement the requirements
   * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
   *
   * @exception LifecycleException if this component detects a fatal error
   *  that prevents this component from being used
   */
  @Override
  protected synchronized void startInternal() throws LifecycleException {
    super.startInternal();

    setState(LifecycleState.STARTING);

    Boolean attachedToValve = false;
    for (Valve valve : getContainer().getPipeline().getValves()) {
      if (valve instanceof RedisSessionHandlerValve) {
        this.handlerValve = (RedisSessionHandlerValve) valve;
        this.handlerValve.setRedisSessionManager(this);
        log.info("Attached to RedisSessionHandlerValve");
        attachedToValve = true;
        break;
      }
    }

    if (!attachedToValve) {
      String error = "Unable to attach to session handling valve; sessions cannot be saved after the request without the valve starting properly.";
      log.fatal(error);
      throw new LifecycleException(error);
    }

    try {
      initializeSerializer();
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
      log.fatal("Unable to load serializer", e);
      throw new LifecycleException(e);
    }

    log.info("Will expire sessions after " + getMaxInactiveInterval() + " seconds");

    initializeDatabaseConnection();

    setDistributable(true);
  }


  /**
   * Stop this component and implement the requirements
   * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
   *
   * @exception LifecycleException if this component detects a fatal error
   *  that prevents this component from being used
   */
  @Override
  protected synchronized void stopInternal() throws LifecycleException {
    if (log.isDebugEnabled()) {
      log.debug("Stopping");
    }

    setState(LifecycleState.STOPPING);

    try {
      connectionPool.destroy();
    } catch(Exception e) {
      // Do nothing.
    }

    // Require a new random number generator if we are restarted
    super.stopInternal();
  }

  @Override
  public Session createSession(String requestedSessionId) {
    RedisSession session = null;
    String sessionId = null;
    String jvmRoute = getJvmRoute();

    Boolean error = true;
    Jedis jedis = null;
    try {
      jedis = acquireConnection();

      // Ensure generation of a unique session identifier.
      if (null != requestedSessionId) {
        sessionId = sessionIdWithJvmRoute(requestedSessionId, jvmRoute);
        if (jedis.setnx(sessionId.getBytes(), NULL_SESSION) == 0L) {
          sessionId = null;
        }
      } else {
        do {
          sessionId = sessionIdWithJvmRoute(generateSessionId(), jvmRoute);
        } while (jedis.setnx(sessionId.getBytes(), NULL_SESSION) == 0L); // 1 = key set; 0 = key already existed
      }

      /* Even though the key is set in Redis, we are not going to flag
         the current thread as having had the session persisted since
         the session isn't actually serialized to Redis yet.
         This ensures that the save(session) at the end of the request
         will serialize the session into Redis with 'set' instead of 'setnx'. */

      error = false;

      if (null != sessionId) {
        session = (RedisSession)createEmptySession();
        session.setNew(true);
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        session.setMaxInactiveInterval(getMaxInactiveInterval());
        session.setId(sessionId);
        session.tellNew();
      }

      currentSession.set(session);
      currentSessionId.set(sessionId);
      currentSessionIsPersisted.set(false);
      currentSessionSerializationMetadata.set(new SessionSerializationMetadata());

      if (null != session) {
        try {
          error = saveInternal(jedis, session, true);
        } catch (IOException ex) {
          log.error("Error saving newly created session: " + ex.getMessage());
          currentSession.set(null);
          currentSessionId.set(null);
          session = null;
        }
      }
    } finally {
      if (jedis != null) {
        returnConnection(jedis, error);
      }
    }

    return session;
  }

  private String sessionIdWithJvmRoute(String sessionId, String jvmRoute) {
    if (jvmRoute != null) {
      String jvmRoutePrefix = '.' + jvmRoute;
      return sessionId.endsWith(jvmRoutePrefix) ? sessionId : sessionId + jvmRoutePrefix;
    }
    return sessionId;
  }

  @Override
  public Session createEmptySession() {
    return new RedisSession(this);
  }

  @Override
  public void add(Session session) {
    try {
      save(session);
    } catch (IOException ex) {
      log.warn("Unable to add to session manager store: " + ex.getMessage());
      throw new RuntimeException("Unable to add to session manager store.", ex);
    }
  }

  @Override
  public Session findSession(String id) throws IOException {
    RedisSession session = null;

    if (null == id) {
      currentSessionIsPersisted.set(false);
      currentSession.set(null);
      currentSessionSerializationMetadata.set(null);
      currentSessionId.set(null);
    } else if (id.equals(currentSessionId.get())) {
      session = currentSession.get();
    } else {
      byte[] data = loadSessionDataFromRedis(id);
      if (data != null) {
        DeserializedSessionContainer container = sessionFromSerializedData(id, data);
        session = container.session;
        currentSession.set(session);
        currentSessionSerializationMetadata.set(container.metadata);
        currentSessionIsPersisted.set(true);
        currentSessionId.set(id);
      } else {
        currentSessionIsPersisted.set(false);
        currentSession.set(null);
        currentSessionSerializationMetadata.set(null);
        currentSessionId.set(null);
      }
    }

    return session;
  }

  public void clear() {
    Jedis jedis = null;
    Boolean error = true;
    try {
      jedis = acquireConnection();
      jedis.flushDB();
      error = false;
    } finally {
      if (jedis != null) {
        returnConnection(jedis, error);
      }
    }
  }

  public int getSize() throws IOException {
    Jedis jedis = null;
    Boolean error = true;
    try {
      jedis = acquireConnection();
      int size = jedis.dbSize().intValue();
      error = false;
      return size;
    } finally {
      if (jedis != null) {
        returnConnection(jedis, error);
      }
    }
  }

  public String[] keys() throws IOException {
    Jedis jedis = null;
    Boolean error = true;
    try {
      jedis = acquireConnection();
      Set<String> keySet = jedis.keys("*");
      error = false;
      return keySet.toArray(new String[keySet.size()]);
    } finally {
      if (jedis != null) {
        returnConnection(jedis, error);
      }
    }
  }

  public byte[] loadSessionDataFromRedis(String id) throws IOException {
    Jedis jedis = null;
    Boolean error = true;

    try {
      log.trace("Attempting to load session " + id + " from Redis");

      jedis = acquireConnection();
      byte[] data = jedis.get(id.getBytes());
      error = false;

      if (data == null) {
        log.trace("Session " + id + " not found in Redis");
      }

      return data;
    } finally {
      if (jedis != null) {
        returnConnection(jedis, error);
      }
    }
  }

  public DeserializedSessionContainer sessionFromSerializedData(String id, byte[] data) throws IOException {
    log.trace("Deserializing session " + id + " from Redis");

    if (Arrays.equals(NULL_SESSION, data)) {
      log.error("Encountered serialized session " + id + " with data equal to NULL_SESSION. This is a bug.");
      throw new IOException("Serialized session data was equal to NULL_SESSION");
    }

    RedisSession session = null;
    SessionSerializationMetadata metadata = new SessionSerializationMetadata();

    try {
      session = (RedisSession)createEmptySession();

      serializer.deserializeInto(data, session, metadata);

      session.setId(id);
      session.setNew(false);
      session.setMaxInactiveInterval(getMaxInactiveInterval());
      session.access();
      session.setValid(true);
      session.resetDirtyTracking();

      if (log.isTraceEnabled()) {
        log.trace("Session Contents [" + id + "]:");
        Enumeration en = session.getAttributeNames();
        while(en.hasMoreElements()) {
          log.trace("  " + en.nextElement());
        }
      }
    } catch (ClassNotFoundException ex) {
      log.fatal("Unable to deserialize into session", ex);
      throw new IOException("Unable to deserialize into session", ex);
    }

    return new DeserializedSessionContainer(session, metadata);
  }

  public void save(Session session) throws IOException {
    save(session, false);
  }

  public void save(Session session, boolean forceSave) throws IOException {
    Jedis jedis = null;
    Boolean error = true;

    try {
      jedis = acquireConnection();
      error = saveInternal(jedis, session, forceSave);
    } catch (IOException e) {
      throw e;
    } finally {
      if (jedis != null) {
        returnConnection(jedis, error);
      }
    }
  }

  protected boolean saveInternal(Jedis jedis, Session session, boolean forceSave) throws IOException {
    Boolean error = true;

    try {
      log.trace("Saving session " + session + " into Redis");

      RedisSession redisSession = (RedisSession)session;

      if (log.isTraceEnabled()) {
        log.trace("Session Contents [" + redisSession.getId() + "]:");
        Enumeration en = redisSession.getAttributeNames();
        while(en.hasMoreElements()) {
          log.trace("  " + en.nextElement());
        }
      }

      byte[] binaryId = redisSession.getId().getBytes();

      Boolean isCurrentSessionPersisted;
      SessionSerializationMetadata sessionSerializationMetadata = currentSessionSerializationMetadata.get();
      byte[] originalSessionAttributesHash = sessionSerializationMetadata.getSessionAttributesHash();
      byte[] sessionAttributesHash = null;
      if (
           forceSave
           || redisSession.isDirty()
           || null == (isCurrentSessionPersisted = this.currentSessionIsPersisted.get())
            || !isCurrentSessionPersisted
           || !Arrays.equals(originalSessionAttributesHash, (sessionAttributesHash = serializer.attributesHashFrom(redisSession)))
         ) {

        log.trace("Save was determined to be necessary");

        if (null == sessionAttributesHash) {
          sessionAttributesHash = serializer.attributesHashFrom(redisSession);
        }

        SessionSerializationMetadata updatedSerializationMetadata = new SessionSerializationMetadata();
        updatedSerializationMetadata.setSessionAttributesHash(sessionAttributesHash);

        jedis.set(binaryId, serializer.serializeFrom(redisSession, updatedSerializationMetadata));

        redisSession.resetDirtyTracking();
        currentSessionSerializationMetadata.set(updatedSerializationMetadata);
        currentSessionIsPersisted.set(true);
      } else {
        log.trace("Save was determined to be unnecessary");
      }

      log.trace("Setting expire timeout on session [" + redisSession.getId() + "] to " + getMaxInactiveInterval());
      jedis.expire(binaryId, getMaxInactiveInterval());

      error = false;

      return error;
    } catch (IOException e) {
      log.error(e.getMessage());

      throw e;
    } finally {
      return error;
    }
  }

  @Override
  public void remove(Session session) {
    remove(session, false);
  }

  @Override
  public void remove(Session session, boolean update) {
    Jedis jedis = null;
    Boolean error = true;

    log.trace("Removing session ID : " + session.getId());

    try {
      jedis = acquireConnection();
      jedis.del(session.getId());
      error = false;
    } finally {
      if (jedis != null) {
        returnConnection(jedis, error);
      }
    }
  }

  public void afterRequest() {
    RedisSession redisSession = currentSession.get();
    if (redisSession != null) {
      try {
        if (redisSession.isValid()) {
          log.trace("Request with session completed, saving session " + redisSession.getId());
          save(redisSession, getAlwaysSaveAfterRequest());
        } else {
          log.trace("HTTP Session has been invalidated, removing :" + redisSession.getId());
          remove(redisSession);
        }
      } catch (Exception e) {
        log.error("Error storing/removing session", e);
      } finally {
        currentSession.remove();
        currentSessionId.remove();
        currentSessionIsPersisted.remove();
        log.trace("Session removed from ThreadLocal :" + redisSession.getIdInternal());
      }
    }
  }

  @Override
  public void processExpires() {
    // We are going to use Redis's ability to expire keys for session expiration.

    // Do nothing.
  }

  private void initializeDatabaseConnection() throws LifecycleException {
    try {
      if (getSentinelMaster() != null) {
        Set<String> sentinelSet = getSentinelSet();
        if (sentinelSet != null && sentinelSet.size() > 0) {
          connectionPool = new JedisSentinelPool(getSentinelMaster(), sentinelSet, this.connectionPoolConfig, getTimeout(), getPassword());
        } else {
          throw new LifecycleException("Error configuring Redis Sentinel connection pool: expected both `sentinelMaster` and `sentiels` to be configured");
        }
      } else {
        connectionPool = new JedisPool(this.connectionPoolConfig, getHost(), getPort(), getTimeout(), getPassword());
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new LifecycleException("Error connecting to Redis", e);
    }
  }

  private void initializeSerializer() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    log.info("Attempting to use serializer :" + serializationStrategyClass);
    serializer = (Serializer) Class.forName(serializationStrategyClass).newInstance();

    Loader loader = null;

    if (getContainer() != null) {
      loader = getContainer().getLoader();
    }

    ClassLoader classLoader = null;

    if (loader != null) {
      classLoader = loader.getClassLoader();
    }
    serializer.setClassLoader(classLoader);
  }


  // Connection Pool Config Accessors

  // - from org.apache.commons.pool2.impl.GenericObjectPoolConfig

  public int getConnectionPoolMaxTotal() {
    return this.connectionPoolConfig.getMaxTotal();
  }

  public void setConnectionPoolMaxTotal(int connectionPoolMaxTotal) {
    this.connectionPoolConfig.setMaxTotal(connectionPoolMaxTotal);
  }

  public int getConnectionPoolMaxIdle() {
    return this.connectionPoolConfig.getMaxIdle();
  }

  public void setConnectionPoolMaxIdle(int connectionPoolMaxIdle) {
    this.connectionPoolConfig.setMaxIdle(connectionPoolMaxIdle);
  }

  public int getConnectionPoolMinIdle() {
    return this.connectionPoolConfig.getMinIdle();
  }

  public void setConnectionPoolMinIdle(int connectionPoolMinIdle) {
    this.connectionPoolConfig.setMinIdle(connectionPoolMinIdle);
  }


  // - from org.apache.commons.pool2.impl.BaseObjectPoolConfig

  public boolean getLifo() {
    return this.connectionPoolConfig.getLifo();
  }
  public void setLifo(boolean lifo) {
    this.connectionPoolConfig.setLifo(lifo);
  }
  public long getMaxWaitMillis() {
    return this.connectionPoolConfig.getMaxWaitMillis();
  }

  public void setMaxWaitMillis(long maxWaitMillis) {
    this.connectionPoolConfig.setMaxWaitMillis(maxWaitMillis);
  }

  public long getMinEvictableIdleTimeMillis() {
    return this.connectionPoolConfig.getMinEvictableIdleTimeMillis();
  }

  public void setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
    this.connectionPoolConfig.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
  }

  public long getSoftMinEvictableIdleTimeMillis() {
    return this.connectionPoolConfig.getSoftMinEvictableIdleTimeMillis();
  }

  public void setSoftMinEvictableIdleTimeMillis(long softMinEvictableIdleTimeMillis) {
    this.connectionPoolConfig.setSoftMinEvictableIdleTimeMillis(softMinEvictableIdleTimeMillis);
  }

  public int getNumTestsPerEvictionRun() {
    return this.connectionPoolConfig.getNumTestsPerEvictionRun();
  }

  public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
    this.connectionPoolConfig.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
  }

  public boolean getTestOnCreate() {
    return this.connectionPoolConfig.getTestOnCreate();
  }

  public void setTestOnCreate(boolean testOnCreate) {
    this.connectionPoolConfig.setTestOnCreate(testOnCreate);
  }

  public boolean getTestOnBorrow() {
    return this.connectionPoolConfig.getTestOnBorrow();
  }

  public void setTestOnBorrow(boolean testOnBorrow) {
    this.connectionPoolConfig.setTestOnBorrow(testOnBorrow);
  }

  public boolean getTestOnReturn() {
    return this.connectionPoolConfig.getTestOnReturn();
  }

  public void setTestOnReturn(boolean testOnReturn) {
    this.connectionPoolConfig.setTestOnReturn(testOnReturn);
  }

  public boolean getTestWhileIdle() {
    return this.connectionPoolConfig.getTestWhileIdle();
  }

  public void setTestWhileIdle(boolean testWhileIdle) {
    this.connectionPoolConfig.setTestWhileIdle(testWhileIdle);
  }

  public long getTimeBetweenEvictionRunsMillis() {
    return this.connectionPoolConfig.getTimeBetweenEvictionRunsMillis();
  }

  public void setTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
    this.connectionPoolConfig.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
  }

  public String getEvictionPolicyClassName() {
    return this.connectionPoolConfig.getEvictionPolicyClassName();
  }

  public void setEvictionPolicyClassName(String evictionPolicyClassName) {
    this.connectionPoolConfig.setEvictionPolicyClassName(evictionPolicyClassName);
  }

  public boolean getBlockWhenExhausted() {
    return this.connectionPoolConfig.getBlockWhenExhausted();
  }

  public void setBlockWhenExhausted(boolean blockWhenExhausted) {
    this.connectionPoolConfig.setBlockWhenExhausted(blockWhenExhausted);
  }

  public boolean getJmxEnabled() {
    return this.connectionPoolConfig.getJmxEnabled();
  }

  public void setJmxEnabled(boolean jmxEnabled) {
    this.connectionPoolConfig.setJmxEnabled(jmxEnabled);
  }
  public String getJmxNameBase() {
    return this.connectionPoolConfig.getJmxNameBase();
  }
  public void setJmxNameBase(String jmxNameBase) {
    this.connectionPoolConfig.setJmxNameBase(jmxNameBase);
  }

  public String getJmxNamePrefix() {
    return this.connectionPoolConfig.getJmxNamePrefix();
  }

  public void setJmxNamePrefix(String jmxNamePrefix) {
    this.connectionPoolConfig.setJmxNamePrefix(jmxNamePrefix);
  }
}

class DeserializedSessionContainer {
  public final RedisSession session;
  public final SessionSerializationMetadata metadata;
  public DeserializedSessionContainer(RedisSession session, SessionSerializationMetadata metadata) {
    this.session = session;
    this.metadata = metadata;
  }
}
