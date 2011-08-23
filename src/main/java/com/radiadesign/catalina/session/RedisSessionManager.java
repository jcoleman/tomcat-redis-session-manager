package com.radiadesign.catalina.session;

import org.apache.catalina.Globals;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.Loader;
import org.apache.catalina.Valve;
import org.apache.catalina.Session;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.ha.session.DeltaSession;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


public class RedisSessionManager extends ManagerBase implements Lifecycle {
  private static Logger log = Logger.getLogger("RedisSessionManager");
  protected String host = "localhost";
  protected int port = 6379;
  protected int database = 0;
  protected JedisPool connectionPool;

  protected RedisSessionHandlerValve handlerValve;
  protected ThreadLocal<DeltaSession> currentSession = new ThreadLocal<DeltaSession>();
  protected Serializer serializer;

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

  public void setSerializationStrategyClass(String strategy) {
    this.serializationStrategyClass = strategy;
  }

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

  public void load() throws ClassNotFoundException, IOException {

  }

  public void unload() throws IOException {

  }

  /**
   * Add a lifecycle event listener to this component.
   *
   * @param listener The listener to add
   */
  public void addLifecycleListener(LifecycleListener listener) {
    lifecycle.addLifecycleListener(listener);
  }


  /**
   * Get the lifecycle listeners associated with this lifecycle. If this
   * Lifecycle has no listeners registered, a zero-length array is returned.
   */
  public LifecycleListener[] findLifecycleListeners() {
    return lifecycle.findLifecycleListeners();
  }


  /**
   * Remove a lifecycle event listener from this component.
   *
   * @param listener The listener to remove
   */
  public void removeLifecycleListener(LifecycleListener listener) {
    lifecycle.removeLifecycleListener(listener);
  }

  @Override
  public Session createEmptySession() {
    return new DeltaSession(this);
  }

  public void start() throws LifecycleException {
    for (Valve valve : getContainer().getPipeline().getValves()) {
      if (valve instanceof RedisSessionHandlerValve) {
        this.handlerValve = (RedisSessionHandlerValve) valve;
        this.handlerValve.setRedisSessionManager(this);
        log.info("Attached to RedisSessionHandlerValve");
        break;
      }
    }

    try {
      initializeSerializer();
    } catch (ClassNotFoundException e) {
      log.log(Level.SEVERE, "Unable to load serializer", e);
      throw new LifecycleException(e);
    } catch (InstantiationException e) {
      log.log(Level.SEVERE, "Unable to load serializer", e);
      throw new LifecycleException(e);
    } catch (IllegalAccessException e) {
      log.log(Level.SEVERE, "Unable to load serializer", e);
      throw new LifecycleException(e);
    }

    log.info("Will expire sessions after " + getMaxInactiveInterval() + " seconds");

    initializeDatabaseConnection();
    
    lifecycle.fireLifecycleEvent(START_EVENT, null);
  }

  public void stop() throws LifecycleException {
    try {
      connectionPool.destroy();
    } catch(Exception e) {
      // Do nothing.
    }
    
    lifecycle.fireLifecycleEvent(STOP_EVENT, null);
  }

  public Session findSession(String id) throws IOException {
    return loadSession(id);
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

  public Session loadSession(String id) throws IOException {
    DeltaSession session;
    
    session = currentSession.get();
    if (session != null) {
      if (id.equals(session.getId())) {
        return session;
      } else {
        currentSession.remove();
      }
    }

    Jedis jedis = null;
    Boolean error = true;

    try {
      log.fine("Loading session " + id + " from Redis");

      session = (DeltaSession)createEmptySession();
      session.setId(id);

      jedis = acquireConnection();
      byte[] data = jedis.get(id.getBytes());
      error = false;

      if (data == null) {
        log.fine("Session " + id + " not found in Redis");
        currentSession.set(session);
        save(session)
        return session;
      }

      session.setMaxInactiveInterval(getMaxInactiveInterval() * 1000);
      session.access();
      session.setValid(true);
      session.setNew(false);
      serializer.deserializeInto(data, session);

      if (log.isLoggable(Level.FINE)) {
        log.fine("Session Contents [" + session.getId() + "]:");
        for (Object name : Collections.list(session.getAttributeNames())) {
            log.fine("  " + name);
        }
      }

      log.fine("Loaded session id " + id);
      currentSession.set(session);
      return session;
    } catch (IOException e) {
      log.severe(e.getMessage());
      throw e;
    } catch (ClassNotFoundException ex) {
      log.log(Level.SEVERE, "Unable to deserialize session ", ex);
      throw new IOException("Unable to deserializeInto session", ex);
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
      log.fine("Saving session " + session + " into Redis");

      DeltaSession deltaSession = (DeltaSession) session;

      if (log.isLoggable(Level.FINE)) {
        log.fine("Session Contents [" + session.getId() + "]:");
        for (Object name : Collections.list(deltaSession.getAttributeNames())) {
            log.fine("  " + name);
        }
      }

      byte[] binaryId = session.getId().getBytes();
      byte[] data = serializer.serializeFrom(deltaSession);

      jedis = acquireConnection();

      if (deltaSession.isDirty()) {
        jedis.set(binaryId, data);
      } else {
        // TODO: Only do this if this session object was not loaded from the database.
        jedis.setnx(binaryId, data);
      }

      jedis.expire(binaryId, getMaxInactiveInterval());
      error = false;

    } catch (IOException e) {
      log.severe(e.getMessage());

      throw e;
    } finally {
      if (jedis != null) {
        returnConnection(jedis, error);
      }
    }
  }

  public void remove(Session session) {
    Jedis jedis = null;
    Boolean error = true;
    
    log.fine("Removing session ID : " + session.getId());
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
    DeltaSession deltaSession = currentSession.get();
    if (deltaSession != null) {
      currentSession.remove();
      log.fine("Session removed from ThreadLocal :" + deltaSession.getIdInternal());
    }
  }

  public void processExpires() {
    // We are going to use Redis's ability to expire keys for session expiration.

    // Do nothing.
  }

  private void initializeDatabaseConnection() throws LifecycleException {
    try {
      // TODO: Allow more parameters (like port).
      connectionPool = new JedisPool(new JedisPoolConfig(), getHost(), getPort());
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
