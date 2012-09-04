package com.radiadesign.catalina.session;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Valve;
import org.apache.catalina.Session;
import org.apache.catalina.session.ManagerBase;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


public class RedisSessionManager extends ManagerBase implements Lifecycle {

  protected byte[] NULL_SESSION = "null".getBytes();

  private final Log log = LogFactory.getLog(RedisSessionManager.class);

  protected String host = "localhost";
  protected int port = 6379;
  protected int database = 0;
  protected String password = null;
  protected int timeout = Protocol.DEFAULT_TIMEOUT;
  protected JedisPool connectionPool;

  protected RedisSessionHandlerValve handlerValve;
  protected ThreadLocal<RedisSession> currentSession = new ThreadLocal<RedisSession>();
  protected ThreadLocal<String> currentSessionId = new ThreadLocal<String>();
  protected ThreadLocal<Boolean> currentSessionIsPersisted = new ThreadLocal<Boolean>();
  protected Serializer serializer;

  protected static String name = "RedisSessionManager";

  protected String serializationStrategyClass = "com.radiadesign.catalina.session.JavaSerializer";

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
    } catch (ClassNotFoundException e) {
      log.fatal("Unable to load serializer", e);
      throw new LifecycleException(e);
    } catch (InstantiationException e) {
      log.fatal("Unable to load serializer", e);
      throw new LifecycleException(e);
    } catch (IllegalAccessException e) {
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
  public Session createSession(String sessionId) {
    RedisSession session = (RedisSession)createEmptySession();

    // Initialize the properties of the new session and return it
    session.setNew(true);
    session.setValid(true);
    session.setCreationTime(System.currentTimeMillis());
    session.setMaxInactiveInterval(getMaxInactiveInterval());

    String jvmRoute = getJvmRoute();

    Boolean error = true;
    Jedis jedis = null;

    try {
      jedis = acquireConnection();

      // Ensure generation of a unique session identifier.
      do {
        if (null == sessionId) {
          sessionId = generateSessionId();
        }

        if (jvmRoute != null) {
          sessionId += '.' + jvmRoute;
        }
      } while (jedis.setnx(sessionId.getBytes(), NULL_SESSION) == 1L); // 1 = key set; 0 = key already existed

      /* Even though the key is set in Redis, we are not going to flag
         the current thread as having had the session persisted since
         the session isn't actually serialized to Redis yet.
         This ensures that the save(session) at the end of the request
         will serialize the session into Redis with 'set' instead of 'setnx'. */

      error = false;

      session.setId(sessionId);
      session.tellNew();

      currentSession.set(session);
      currentSessionId.set(sessionId);
      currentSessionIsPersisted.set(false);
    } finally {
      if (jedis != null) {
        returnConnection(jedis, error);
      }
    }

    return session;
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
    RedisSession session;

    if (id == null) {
      session = null;
      currentSessionIsPersisted.set(false);
    } else if (id.equals(currentSessionId.get())) {
      session = currentSession.get();
    } else {
      session = loadSessionFromRedis(id);

      if (session != null) {
        currentSessionIsPersisted.set(true);
      }
    }

    currentSession.set(session);
    currentSessionId.set(id);

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

  public RedisSession loadSessionFromRedis(String id) throws IOException {
    RedisSession session;

    Jedis jedis = null;
    Boolean error = true;

    try {
      log.trace("Attempting to load session " + id + " from Redis");

      jedis = acquireConnection();
      byte[] data = jedis.get(id.getBytes());
      error = false;

      if (data == null) {
        log.trace("Session " + id + " not found in Redis");
        session = null;
      } else if (Arrays.equals(NULL_SESSION, data)) {
        throw new IllegalStateException("Race condition encountered: attempted to load session[" + id + "] which has been created but not yet serialized.");
      } else {
        log.trace("Deserializing session " + id + " from Redis");
        session = (RedisSession)createEmptySession();
        serializer.deserializeInto(data, session);
        session.setId(id);
        session.setNew(false);
        session.setMaxInactiveInterval(getMaxInactiveInterval() * 1000);
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
      }

      return session;
    } catch (IOException e) {
      log.fatal(e.getMessage());
      throw e;
    } catch (ClassNotFoundException ex) {
      log.fatal("Unable to deserialize into session", ex);
      throw new IOException("Unable to deserialize into session", ex);
    } finally {
      if (jedis != null) {
        returnConnection(jedis, error);
      }
    }
  }

  public void save(Session session) throws IOException {
    Jedis jedis = null;
    Boolean error = true;

    try {
      log.trace("Saving session " + session + " into Redis");

      RedisSession redisSession = (RedisSession) session;

      if (log.isTraceEnabled()) {
        log.trace("Session Contents [" + redisSession.getId() + "]:");
        Enumeration en = redisSession.getAttributeNames();
        while(en.hasMoreElements()) {
          log.trace("  " + en.nextElement());
        }
      }

      Boolean sessionIsDirty = redisSession.isDirty();

      redisSession.resetDirtyTracking();
      byte[] binaryId = redisSession.getId().getBytes();

      jedis = acquireConnection();

      if (sessionIsDirty || currentSessionIsPersisted.get() != true) {
        jedis.set(binaryId, serializer.serializeFrom(redisSession));
      }

      currentSessionIsPersisted.set(true);

      log.trace("Setting expire timeout on session [" + redisSession.getId() + "] to " + getMaxInactiveInterval());
      jedis.expire(binaryId, getMaxInactiveInterval());

      error = false;
    } catch (IOException e) {
      log.error(e.getMessage());

      throw e;
    } finally {
      if (jedis != null) {
        returnConnection(jedis, error);
      }
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
      currentSession.remove();
      currentSessionId.remove();
      currentSessionIsPersisted.remove();
      log.trace("Session removed from ThreadLocal :" + redisSession.getIdInternal());
    }
  }

  @Override
  public void processExpires() {
    // We are going to use Redis's ability to expire keys for session expiration.

    // Do nothing.
  }

  private void initializeDatabaseConnection() throws LifecycleException {
    try {
      // TODO: Allow configuration of pool (such as size...)
      connectionPool = new JedisPool(new JedisPoolConfig(), getHost(), getPort(), getTimeout(), getPassword());
    } catch (Exception e) {
      e.printStackTrace();
      throw new LifecycleException("Error Connecting to Redis", e);
    }
  }

  private void initializeSerializer() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    log.info("Attempting to use serializer :" + serializationStrategyClass);
    serializer = (Serializer) Class.forName(serializationStrategyClass).newInstance();

    Loader loader = null;

    if (container != null) {
      loader = container.getLoader();
    }

    ClassLoader classLoader = null;

    if (loader != null) {
        classLoader = loader.getClassLoader();
    }
    serializer.setClassLoader(classLoader);
  }
}
