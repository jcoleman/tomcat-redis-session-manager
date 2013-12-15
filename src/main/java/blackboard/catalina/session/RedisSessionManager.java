package blackboard.catalina.session;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.connector.Request;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.session.ManagerBase;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;

import java.io.IOException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


public class RedisSessionManager extends ManagerBase implements Lifecycle, RedisSessionFactory {

  private final Log log = LogFactory.getLog(RedisSessionManager.class);

  protected String host = "localhost";
  protected int port = 6379;
  protected int database = 0;
  protected String password = null;
  protected int timeout = Protocol.DEFAULT_TIMEOUT;
  protected JedisPool connectionPool;

  protected String managerId;
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

    try {
      initializeSerializer();
    } catch (Exception e) {
      log.fatal("Unable to load serializer", e);
      throw new LifecycleException(e);
    }

    initializeDatabaseConnection();
    setDistributable(true);
    managerId = generateSessionId();
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
      log.error( e.getMessage(), e );
    }

    // Require a new random number generator if we are restarted
    super.stopInternal();
  }

  private byte[] getSessionKey( String sessionId ) {
    return (managerId + "-" + sessionId).getBytes();
  }

  @Override
  protected StandardSession getNewSession()
  {
    return createRedisSession();
  }

  public RedisSession createRedisSession()
  {
    return new RedisSession( this );
  }

  public RedisSession loadSession(final String id) throws IOException {
    return execute( null, new SessionOperation()
    {

      @Override
      public RedisSession execute( Jedis jedis, RedisSession session ) throws Exception
      {
        byte[] data = jedis.get(getSessionKey( id ));

        if (data == null) {
          log.trace("Session " + id + " not found in Redis");
          return null;
        }

        log.trace("Deserializing session " + id + " from Redis");
        session = (RedisSession)serializer.readSession( data, RedisSessionManager.this );

        session.setId( id );
        session.setNew( false );
        session.access();
        session.setValid(true);
        session.resetDirtyTracking();
        return session;
      }

    } );
  }

  public void save(Session session) {
    log.trace("Saving session " + session + " into Redis");

    RedisSession redisSession = (RedisSession) session;
    if (redisSession.isDirty()) {
      execute( redisSession, new SessionOperation()
      {

        @Override
        public RedisSession execute( Jedis jedis, RedisSession session ) throws Exception
        {
          jedis.set(getSessionKey( session.getId() ), serializer.writeSession( session ));
          session.resetDirtyTracking();
          return session;
        }

      } );
    }
  }

  @Override
  public void remove(final Session session, boolean update) {
    super.remove( session, update );

    log.trace( "Removing session ID : " + session.getId() );

    execute( (RedisSession) session, new SessionOperation()
    {

      @Override
      public RedisSession execute( Jedis jedis, RedisSession session )
      {
        jedis.del( getSessionKey( session.getId() ) );
        return null;
      }

    } );
  }

  
  private RedisSession execute( RedisSession session, SessionOperation operation )
  {
    Jedis jedis = null;
    boolean error = false;

    try {
      jedis = connectionPool.getResource();

      if (getDatabase() != 0) {
        jedis.select(getDatabase());
      }

      return operation.execute( jedis, session );
    } catch ( Exception err ) {
      error = true;
      throw new IllegalStateException( err );
    } finally {
      if ( jedis != null )
      {
        if (error) {
          connectionPool.returnBrokenResource(jedis);
        } else {
          connectionPool.returnResource(jedis);
        }
      }
    }
  }

  public void beforeRequest( Request request ) throws IOException
  {
    RedisSession session = loadSession( request.getRequestedSessionId() );

    if ( session != null )
    {
      sessions.put( session.getIdInternal(), session );
    }
  }

  public void afterRequest( Request request ) throws IOException
  {
    RedisSession session = (RedisSession)request.getSessionInternal( false );

    if (session != null && session.isValid() && session.isDirty() ) {
      save( session );
    }
  }

  private void initializeDatabaseConnection() throws LifecycleException {
    try {
      // TODO: Allow configuration of pool (such as size...)
      connectionPool = new JedisPool(new JedisPoolConfig(), getHost(), getPort(), getTimeout(), getPassword());
    } catch (Exception e) {
      log.error( e.getMessage(), e );
      throw new LifecycleException("Error Connecting to Redis", e);
    }
  }

  private void initializeSerializer() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    log.info("Attempting to use serializer :" + serializationStrategyClass);
    serializer = (Serializer) Class.forName(serializationStrategyClass).newInstance();
  }

  public ClassLoader getSessionClassLoader()
  {
    if (container == null) {
      return null;
    }

    return container.getLoader().getClassLoader();
  }

  private static interface SessionOperation
  {

    public RedisSession execute( Jedis jedis, RedisSession session ) throws Exception;

  }

}
