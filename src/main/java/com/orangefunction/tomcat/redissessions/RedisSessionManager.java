package com.orangefunction.tomcat.redissessions;

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
import java.util.HashSet;
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


public class RedisSessionManager extends ManagerBase implements Lifecycle, RedisSessionFactory
{

  private final Log log = LogFactory.getLog( RedisSessionManager.class );

  protected String host = "localhost";
  protected int port = 6379;
  protected int database = 0;
  protected String password = null;
  protected int timeout = Protocol.DEFAULT_TIMEOUT;
  protected JedisPool connectionPool;
  protected String managerId;
  protected Serializer serializer;
  protected String serializationStrategyClass = JavaSerializer.class.getName();
  protected Set<String> sessionsToLoad = new HashSet<String>();

  /**
   * The lifecycle event support for this component.
   */
  protected LifecycleSupport lifecycle = new LifecycleSupport( this );


  public String getHost()
  {
    return host;
  }

  public void setHost( String host )
  {
    this.host = host;
  }

  public int getPort()
  {
    return port;
  }

  public void setPort( int port )
  {
    this.port = port;
  }

  public int getDatabase()
  {
    return database;
  }

  public void setDatabase( int database )
  {
    this.database = database;
  }

  public int getTimeout()
  {
    return timeout;
  }

  public void setTimeout( int timeout )
  {
    this.timeout = timeout;
  }

  public String getPassword()
  {
    return password;
  }

  public void setPassword( String password )
  {
    this.password = password;
  }

  public void setSerializationStrategyClass( String strategy )
  {
    this.serializationStrategyClass = strategy;
  }

  @Override
  public void load() throws ClassNotFoundException, IOException
  {
  }

  @Override
  public void unload() throws IOException
  {

  }

  /**
   * Add a lifecycle event listener to this component.
   *
   * @param listener The listener to add
   */
  @Override
  public void addLifecycleListener( LifecycleListener listener )
  {
    lifecycle.addLifecycleListener( listener );
  }

  /**
   * Get the lifecycle listeners associated with this lifecycle. If this
   * Lifecycle has no listeners registered, a zero-length array is returned.
   */
  @Override
  public LifecycleListener[] findLifecycleListeners()
  {
    return lifecycle.findLifecycleListeners();
  }

  /**
   * Remove a lifecycle event listener from this component.
   *
   * @param listener The listener to remove
   */
  @Override
  public void removeLifecycleListener( LifecycleListener listener )
  {
    lifecycle.removeLifecycleListener( listener );
  }

  /**
   * Start this component and implement the requirements
   * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
   *
   * @throws LifecycleException if this component detects a fatal error
   *                            that prevents this component from being used
   */
  @Override
  protected synchronized void startInternal() throws LifecycleException
  {
    super.startInternal();

    setState( LifecycleState.STARTING );

    try
    {
      initializeSerializer();
    } catch ( Exception e )
    {
      log.fatal( "Unable to load serializer", e );
      throw new LifecycleException( e );
    }

    initializeDatabaseConnection();
    setDistributable( true );
    managerId = generateSessionId();
  }

  /**
   * Stop this component and implement the requirements
   * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
   *
   * @throws LifecycleException if this component detects a fatal error
   *                            that prevents this component from being used
   */
  @Override
  protected synchronized void stopInternal() throws LifecycleException
  {
    if ( log.isDebugEnabled() )
    {
      log.debug( "Stopping" );
    }

    setState( LifecycleState.STOPPING );

    try
    {
      connectionPool.destroy();
    } catch ( Exception e )
    {
      // Do nothing.
      log.error( e.getMessage(), e );
    }

    // Require a new random number generator if we are restarted
    super.stopInternal();
  }

  byte[] getSessionKey( String sessionId )
  {
    return ( managerId + ":" + sessionId ).getBytes();
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

  @Override
  public Session findSession( String id ) throws IOException
  {
    if ( sessionsToLoad.contains( id ) )
    {
      loadSession( id );
    }

    return super.findSession( id );
  }

  void loadSession( final String id )
  {
    execute( new SessionOperation()
    {

      @Override
      public void execute( Jedis jedis ) throws Exception
      {
        byte[] data = jedis.get( getSessionKey( id ) );

        if ( data == null )
        {
          log.trace( "Session " + id + " not found in Redis" );
          return;
        }

        log.trace( "Deserializing redissessions " + id + " from Redis" );
        RedisSession session = (RedisSession) serializer.readSession( data, RedisSessionManager.this );

        session.setId( id );
        session.setNew( false );
        session.access();
        session.setValid( true );
        session.resetDirtyTracking();

        sessionsToLoad.remove( id );
        sessions.put( id, session );
      }

    } );
  }

  RedisSession getSessionInternal( String id )
  {
    return (RedisSession) sessions.get( id );
  }

  void saveSession( Session session )
  {
    log.trace( "Saving redissessions " + session + " into Redis" );

    final RedisSession redisSession = (RedisSession) session;
    if ( redisSession.isDirty() )
    {
      execute( new SessionOperation()
      {

        @Override
        public void execute( Jedis jedis ) throws Exception
        {
          jedis.set( getSessionKey( redisSession.getId() ), serializer.writeSession( redisSession ) );
          redisSession.resetDirtyTracking();
        }

      } );
    }
  }

  @Override
  public void remove( final Session session, boolean update )
  {
    super.remove( session, update );

    log.trace( "Removing redissessions ID : " + session.getId() );

    execute( new SessionOperation()
    {

      @Override
      public void execute( Jedis jedis )
      {
        jedis.del( getSessionKey( session.getId() ) );
      }

    } );
  }

  void returnConnection( Jedis jedis, boolean error )
  {
    if ( jedis == null )
    {
      return;
    }

    if ( error )
    {
      connectionPool.returnBrokenResource( jedis );
    } else
    {
      connectionPool.returnResource( jedis );
    }
  }

  protected void execute( SessionOperation operation )
  {
    Jedis jedis = null;
    boolean error = false;

    try
    {
      jedis = connectionPool.getResource();

      if ( getDatabase() != 0 )
      {
        jedis.select( getDatabase() );
      }

      operation.execute( jedis );
    } catch ( Exception err )
    {
      error = true;
      operation.onFailure( jedis, err );
    } finally
    {
      returnConnection( jedis, error );
    }
  }

  public void beforeRequest( Request request ) throws IOException
  {
    if ( request.getRequestedSessionId() != null )
    {
      sessionsToLoad.add( request.getRequestedSessionId() );
    }
  }

  public void afterRequest( Request request ) throws IOException
  {
    sessionsToLoad.remove( request.getRequestedSessionId() );
    RedisSession session = (RedisSession) request.getSessionInternal( false );

    if ( session != null && session.isValid() && session.isDirty() )
    {
      saveSession( session );
    }
  }

  void initializeDatabaseConnection() throws LifecycleException
  {
    try
    {
      // TODO: Allow configuration of pool (such as size...)
      connectionPool = new JedisPool( new JedisPoolConfig(), getHost(), getPort(), getTimeout(), getPassword() );
    } catch ( Exception e )
    {
      log.error( e.getMessage(), e );
      throw new LifecycleException( "Error Connecting to Redis", e );
    }
  }

  void initializeSerializer() throws ClassNotFoundException, IllegalAccessException, InstantiationException
  {
    log.info( "Attempting to use serializer :" + serializationStrategyClass );
    serializer = (Serializer) Class.forName( serializationStrategyClass ).newInstance();
  }

  public ClassLoader getSessionClassLoader()
  {
    if ( container == null )
    {
      return null;
    }

    return container.getLoader().getClassLoader();
  }

  protected abstract class SessionOperation
  {

    public abstract void execute( Jedis jedis ) throws Exception;

    public void onFailure( Jedis jedis, Exception err )
    {
      getContainer().getLogger().error( err.getMessage(), err );
    }

  }

}
