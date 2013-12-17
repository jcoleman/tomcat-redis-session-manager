package blackboard.catalina.session;

import org.apache.catalina.Container;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.ServletException;
import java.io.IOException;

import static org.mockito.Mockito.*;

@RunWith( MockitoJUnitRunner.class )
public class RedisSessionHandlerValveTest
{

  @Mock
  private Request _mockRequest;

  @Mock
  private Response _mockResponse;

  @Mock
  private RedisSessionManager _mockManager;

  @Mock
  private Container _mockContainer;

  @Mock
  private Valve _next;

  private RedisSessionHandlerValve _valve;

  @Before
  public void setUp()
  {
    when( _mockContainer.getManager() ).thenReturn( _mockManager );

    RedisSessionHandlerValve valve = new RedisSessionHandlerValve();
    valve.setContainer( _mockContainer );
    valve.setNext( _next );
    _valve = valve;
  }

  @Test
  public void invoke_callsBeforeRequestBeforeNextInvoke() throws IOException, ServletException
  {
    _valve.invoke( _mockRequest, _mockResponse );

    InOrder inOrder = inOrder( _mockManager, _next );
    inOrder.verify( _mockManager ).beforeRequest( _mockRequest );
    inOrder.verify( _next ).invoke( _mockRequest, _mockResponse );
  }

  @Test
  public void invoke_callsAfterRequestAfterNextInvoke() throws IOException, ServletException
  {
    _valve.invoke( _mockRequest, _mockResponse );

    InOrder inOrder = inOrder( _next, _mockManager );
    inOrder.verify( _next ).invoke( _mockRequest, _mockResponse );
    inOrder.verify( _mockManager ).afterRequest( _mockRequest );
  }

  @Test( expected = RuntimeException.class )
  public void invoke_callsAfterRequestEvenOnException() throws IOException, ServletException
  {
    doThrow( new RuntimeException() ).when( _next ).invoke( _mockRequest, _mockResponse );

    _valve.invoke( _mockRequest, _mockResponse );

    verify( _mockManager ).afterRequest( _mockRequest );
  }

}
