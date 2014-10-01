package com.orangefunction.tomcat.redissessions;

import org.apache.catalina.*;
import org.apache.catalina.connector.Request;
import org.apache.juli.logging.Log;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith( MockitoJUnitRunner.class )
public class RedisSessionManagerTest
{

  @Mock
  private Context _mockContext;

  @Mock
  private Container _mockContainer;

  @Mock
  private JedisPool _mockPool;

  @Mock
  private Jedis _mockJedis;

  private RedisSessionManager _manager;

  @Before
  public void setUp()
  {
    when( _mockContainer.getName() ).thenReturn( "bar" );

    when( _mockContext.getName() ).thenReturn( "foo" );

    Log log = mock( Log.class );
    doAnswer( new Answer()
    {
      @Override
      public Object answer( InvocationOnMock invocation ) throws Throwable
      {
        System.err.println( invocation.getArguments()[0] );
        Throwable t = (Throwable) invocation.getArguments()[1];
        t.printStackTrace();
        return null;
      }

    } ).when( log ).error( anyString(), any( Throwable.class ) );

    when( _mockContext.getLogger() ).thenReturn( log );
    when( _mockContext.getParent() ).thenReturn( _mockContainer );

    Loader loader = mock( Loader.class );
    when( loader.getClassLoader() ).thenReturn( this.getClass().getClassLoader() );
    when( _mockContext.getLoader() ).thenReturn( loader );

    when( _mockPool.getResource() ).thenReturn( _mockJedis );

    RedisSessionManager cut = new RedisSessionManager();
    cut.setContainer( _mockContext );
    _manager = cut;
  }



  @Test
  public void startInternal_initializesSerializer() throws LifecycleException, IllegalAccessException, InstantiationException, ClassNotFoundException
  {
    RedisSessionManager cut = spy( _manager );
    cut.start();
    verify( cut ).initializeSerializer();
  }

  @Test
  public void startInternal_initializesJedisPool() throws LifecycleException, IllegalAccessException, InstantiationException, ClassNotFoundException
  {
    RedisSessionManager cut = spy( _manager );
    cut.start();
    verify( cut ).initializeDatabaseConnection();
  }

  @Test
  public void startInternal_initializesManagerId() throws LifecycleException, IllegalAccessException, InstantiationException, ClassNotFoundException
  {
    _manager.start();
    assertThat( _manager.managerId, is( notNullValue() ) );
  }


  @Test
  public void stopInternal_destroysConnectionPool() throws LifecycleException, IllegalAccessException, InstantiationException, ClassNotFoundException
  {
    _manager.start();

    _manager.connectionPool = spy( _manager.connectionPool );
    _manager.stop();

    verify( _manager.connectionPool ).destroy();
  }

  @Test
  public void getSessionKey_concatenatesManagerIdAndSessionId()
  {
    _manager.managerId = "foo";
    assertThat( _manager.getSessionKey( "bar" ), is( "foo:bar".getBytes() ) );
  }


  private RedisSession createSession( RedisSessionManager manager, String id )
  {
    RedisSession session = new RedisSession( manager );
    session.setId( id );
    session.setValid( true );
    session.setCreationTime( System.currentTimeMillis() );
    return session;
  }

  private void mockSerializedSession( RedisSessionManager manager, String id ) throws IOException
  {
    RedisSession session = createSession( manager, id );
    byte[] bytes = manager.serializer.writeSession( session );
    when( _mockJedis.get( manager.getSessionKey( id ) ) ).thenReturn( bytes );
  }

  @Test
  public void findSession_callsLoadSessionOnFirstFind() throws IOException, LifecycleException
  {
    RedisSessionManager cut = spy( _manager );
    cut.start();
    cut.connectionPool = _mockPool;
    mockSerializedSession( cut, "foo" );

    cut.sessionsToLoad.add( "foo" );
    cut.findSession( "foo" );
    verify( cut ).loadSession( "foo" );
  }

  @Test
  public void findSession_doesNotcallLoadSessionOnSecondFind() throws IOException, LifecycleException
  {
    RedisSessionManager cut = spy( _manager );
    cut.start();
    cut.connectionPool = _mockPool;
    mockSerializedSession( cut, "foo" );

    cut.sessionsToLoad.add( "foo" );
    cut.findSession( "foo" );
    cut.findSession( "foo" );
    verify( cut, times( 1 ) ).loadSession( "foo" );
  }

  @Test
  public void loadSession_nullDataSkipsCreatingSession() throws IOException, LifecycleException
  {
    RedisSessionManager cut = _manager;
    cut.start();
    cut.connectionPool = _mockPool;
    //mockSerializedSession( cut, "foo" );

    cut.loadSession( "foo" );
    assertThat( cut.getSessionInternal( "foo" ), is( nullValue() ) );
  }

  @Test
  public void loadSession_validDataCreatesSession() throws IOException, LifecycleException
  {
    RedisSessionManager cut = _manager;
    cut.start();
    cut.connectionPool = _mockPool;
    mockSerializedSession( cut, "foo" );

    cut.loadSession( "foo" );
    assertThat( cut.getSessionInternal( "foo" ), is( notNullValue() ) );
  }

  @Test
  public void loadSession_marksSessionAsNotNew() throws IOException, LifecycleException
  {
    RedisSessionManager cut = _manager;
    cut.start();
    cut.connectionPool = _mockPool;
    mockSerializedSession( cut, "foo" );

    cut.loadSession( "foo" );
    RedisSession session = cut.getSessionInternal( "foo" );
    assertThat( session.isNew(), is( false ) );
  }

  @Test
  public void loadSession_marksSessionAsNotDirty() throws IOException, LifecycleException
  {
    RedisSessionManager cut = _manager;
    cut.start();
    cut.connectionPool = _mockPool;
    mockSerializedSession( cut, "foo" );

    cut.loadSession( "foo" );
    RedisSession session = cut.getSessionInternal( "foo" );
    assertThat( session.isDirty(), is( false ) );
  }

  @Test
  public void loadSession_marksSessionAsValid() throws IOException, LifecycleException
  {
    RedisSessionManager cut = _manager;
    cut.start();
    cut.connectionPool = _mockPool;
    mockSerializedSession( cut, "foo" );

    cut.loadSession( "foo" );
    RedisSession session = cut.getSessionInternal( "foo" );
    assertThat( session.isValid(), is( true ) );
  }

  @Test
  public void saveSession_doesNotCallExecuteIfNotDirty() throws IOException, LifecycleException
  {
    RedisSessionManager cut = spy( _manager );
    cut.start();
    cut.connectionPool = _mockPool;

    RedisSession session = createSession( cut, "foo" );
    cut.saveSession( session );

    verify( cut, never() ).execute( any( RedisSessionManager.SessionOperation.class ) );
  }

  @Test
  public void saveSession_executesSetOnRedisIfDirty() throws IOException, LifecycleException
  {
    RedisSessionManager cut = _manager;
    cut.start();
    cut.connectionPool = _mockPool;

    RedisSession session = createSession( cut, "foo" );
    session.setAttribute( "foo", "bar" );

    cut.saveSession( session );
    verify( _mockJedis ).set( _manager.getSessionKey( "foo" ), _manager.serializer.writeSession( session ) );
  }

  @Test
  public void saveSession_resetsDirtyFlag() throws IOException, LifecycleException
  {
    RedisSessionManager cut = _manager;
    cut.start();
    cut.connectionPool = _mockPool;

    RedisSession session = createSession( cut, "foo" );
    session.setAttribute( "foo", "bar" );

    cut.saveSession( session );
    assertThat( session.isDirty(), is( false ) );
  }

  @Test
  public void remove_callsJedisDelCommand() throws IOException, LifecycleException
  {
    RedisSessionManager cut = _manager;
    cut.start();
    cut.connectionPool = _mockPool;

    RedisSession session = createSession( cut, "foo" );

    cut.remove( session, false );
    verify( _mockJedis ).del( _manager.getSessionKey( "foo" ) );
  }


  @Test
  public void returnConnection_returnsBrokenWithError() throws IOException, LifecycleException
  {
    RedisSessionManager cut = _manager;
    cut.connectionPool = _mockPool;

    cut.returnConnection( _mockJedis, true );
    verify( _mockPool ).returnBrokenResource( _mockJedis );
  }

  @Test
  public void returnConnection_returnsWithoutError() throws IOException, LifecycleException
  {
    RedisSessionManager cut = _manager;
    cut.connectionPool = _mockPool;

    cut.returnConnection( _mockJedis, false );
    verify( _mockPool ).returnResource( _mockJedis );
  }

  @Test
  public void returnConnection_neverReturnsOnNull() throws IOException, LifecycleException
  {
    RedisSessionManager cut = _manager;
    cut.connectionPool = _mockPool;

    cut.returnConnection( null, false );
    verify( _mockPool, never() ).returnResource( any( Jedis.class ) );
    verify( _mockPool, never() ).returnBrokenResource( any( Jedis.class ) );
  }

  @Test
  public void execute_selectsDatabaseWhenNotZero() throws IOException, LifecycleException
  {
    RedisSessionManager cut = _manager;
    cut.connectionPool = _mockPool;
    cut.setDatabase( 1 );

    cut.execute( mock( RedisSessionManager.SessionOperation.class ) );
    verify( _mockJedis ).select( 1 );
  }

  @Test
  public void execute_callsExecuteOnOperation() throws Exception
  {
    RedisSessionManager cut = _manager;
    cut.connectionPool = _mockPool;

    RedisSessionManager.SessionOperation operation = mock( RedisSessionManager.SessionOperation.class );
    cut.execute( operation );

    verify( operation ).execute( _mockJedis );
  }


  @Test
  public void execute_callsOnFailureWithException() throws Exception
  {
    RedisSessionManager cut = _manager;
    cut.connectionPool = _mockPool;

    Exception ex = new Exception();
    RedisSessionManager.SessionOperation operation = mock( RedisSessionManager.SessionOperation.class );
    doThrow( ex ).when( operation ).execute( _mockJedis );

    cut.execute( operation );
    verify( operation ).onFailure( _mockJedis, ex );
  }

  @Test
  public void execute_returnsConnectionWithoutErrorWithException() throws Exception
  {
    RedisSessionManager cut = spy( _manager );
    cut.connectionPool = _mockPool;

    RedisSessionManager.SessionOperation operation = mock( RedisSessionManager.SessionOperation.class );
    cut.execute( operation );
    verify( cut ).returnConnection( _mockJedis, false );
  }

  @Test
  public void execute_returnsConnectionWithErrorWithException() throws Exception
  {
    RedisSessionManager cut = spy( _manager );
    cut.connectionPool = _mockPool;

    Exception ex = new Exception();
    RedisSessionManager.SessionOperation operation = mock( RedisSessionManager.SessionOperation.class );
    doThrow( ex ).when( operation ).execute( _mockJedis );

    cut.execute( operation );
    verify( cut ).returnConnection( _mockJedis, true );
  }

  @Test
  public void beforeRequest_addsSessionToLoadSet() throws Exception
  {
    RedisSessionManager cut = _manager;

    Request request = mock( Request.class );
    when( request.getRequestedSessionId() ).thenReturn( "foo" );

    cut.beforeRequest( request );

    assertThat( _manager.sessionsToLoad, hasItem( "foo" ) );
  }

  @Test
  public void beforeRequest_handlesNullSessionId() throws Exception
  {
    RedisSessionManager cut = _manager;

    Request request = mock( Request.class );

    cut.beforeRequest( request );

    assertThat( _manager.sessionsToLoad, is( empty() ));
  }

  @Test
  public void afterRequest_removesSessionFromLoadSet() throws Exception
  {
    RedisSessionManager cut = _manager;
    cut.sessionsToLoad.add( "foo" );

    Request request = mock( Request.class );
    when( request.getRequestedSessionId() ).thenReturn( "foo" );

    cut.afterRequest( request );

    assertThat( _manager.sessionsToLoad, is( empty() ) );
  }

  @Test
  public void afterRequest_savesSessionIfNotNullAndIsDirty() throws Exception
  {
    RedisSessionManager cut = spy( _manager );
    cut.start();
    cut.connectionPool = _mockPool;
    cut.sessionsToLoad.add( "foo" );

    Request request = mock( Request.class );
    when( request.getRequestedSessionId() ).thenReturn( "foo" );

    RedisSession session = createSession( cut, "foo" );
    session.setValid( true );
    session.setAttribute( "foo", "bar" );

    when( request.getSessionInternal( false ) ).thenReturn( session );

    cut.afterRequest( request );
    verify( cut ).saveSession( session );
  }

  @Test
  public void afterRequest_neverSavesSessionIfNull() throws Exception
  {
    RedisSessionManager cut = spy( _manager );
    cut.start();
    cut.connectionPool = _mockPool;
    cut.sessionsToLoad.add( "foo" );

    Request request = mock( Request.class );
    when( request.getRequestedSessionId() ).thenReturn( "foo" );

    cut.afterRequest( request );
    verify( cut, never() ).saveSession( any( Session.class ) );
  }

  @Test
  public void afterRequest_neverSavesSessionIfNotDirty() throws Exception
  {
    RedisSessionManager cut = spy( _manager );
    cut.start();
    cut.connectionPool = _mockPool;
    cut.sessionsToLoad.add( "foo" );

    Request request = mock( Request.class );
    when( request.getRequestedSessionId() ).thenReturn( "foo" );

    RedisSession session = createSession( cut, "foo" );
    session.setValid( true );
    session.resetDirtyTracking();

    when( request.getSessionInternal( false ) ).thenReturn( session );

    cut.afterRequest( request );
    verify( cut, never() ).saveSession( any( Session.class ) );
  }

  @Test
  public void afterRequest_neverSavesSessionIfNotValid() throws Exception
  {
    RedisSessionManager cut = spy( _manager );
    cut.start();
    cut.connectionPool = _mockPool;
    cut.sessionsToLoad.add( "foo" );

    Request request = mock( Request.class );
    when( request.getRequestedSessionId() ).thenReturn( "foo" );

    RedisSession session = createSession( cut, "foo" );
    session.setAttribute( "foo", "bar" );
    session.setValid( false );

    when( request.getSessionInternal( false ) ).thenReturn( session );

    cut.afterRequest( request );
    verify( cut, never() ).saveSession( any( Session.class ) );
  }

}
