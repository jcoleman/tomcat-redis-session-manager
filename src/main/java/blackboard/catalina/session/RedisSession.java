package blackboard.catalina.session;

import java.security.Principal;

import org.apache.catalina.Manager;
import org.apache.catalina.session.StandardSession;

import java.util.HashMap;


public class RedisSession extends StandardSession
{

  protected boolean dirty = false;

  public RedisSession( Manager manager )
  {
    super( manager );
    resetDirtyTracking();
  }

  public boolean isDirty()
  {
    return dirty;
  }

  public void resetDirtyTracking()
  {
    dirty = false;
  }

  @Override
  public void setAttribute( String key, Object value )
  {
    dirty = true;
    super.setAttribute( key, value );
  }

  @Override
  public void removeAttribute( String name )
  {
    dirty = true;
    super.removeAttribute( name );
  }

  @Override
  public void setPrincipal( Principal principal )
  {
    dirty = true;
    super.setPrincipal( principal );
  }

}
