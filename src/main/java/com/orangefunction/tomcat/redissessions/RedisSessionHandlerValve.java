package com.orangefunction.tomcat.redissessions;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import javax.servlet.ServletException;
import java.io.IOException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


public class RedisSessionHandlerValve extends ValveBase
{
  private final Log log = LogFactory.getLog( RedisSessionManager.class );

  @Override
  public void invoke( Request request, Response response ) throws IOException, ServletException
  {
    RedisSessionManager manager = (RedisSessionManager) getContainer().getManager();

    try
    {
      manager.beforeRequest( request );
      getNext().invoke( request, response );
    } finally
    {
      manager.afterRequest( request );
    }
  }

}
