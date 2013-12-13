package com.radiadesign.catalina.session;

import org.apache.catalina.Manager;
import org.apache.catalina.session.StandardSession;

import java.util.Enumeration;

/**
 * Created by david on 12/12/13.
 */
public class RedisAttributeSession extends StandardSession
{

  private RedisSessionManager _redisSessionManager;

  /**
   * Construct a new Session associated with the specified Manager.
   *
   * @param manager The manager with which this Session is associated
   */
  public RedisAttributeSession( RedisSessionManager manager )
  {
    super( manager );
    _redisSessionManager = manager;
  }

  @Override
  public Object getAttribute( String name )
  {
    return super.getAttribute( name );
  }

  @Override
  public Enumeration<String> getAttributeNames()
  {
    return super.getAttributeNames();
  }

  @Override
  public void removeAttribute( String name )
  {
    super.removeAttribute( name );
  }

  @Override
  public void setAttribute( String name, Object value )
  {
    super.setAttribute( name, value );
  }

  @Override
  public void invalidate()
  {
    super.invalidate();
  }
}
