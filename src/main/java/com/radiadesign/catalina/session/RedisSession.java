package com.radiadesign.catalina.session;

import org.apache.catalina.Manager;
import org.apache.catalina.session.StandardSession;
import java.util.HashMap;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Logger;


public class RedisSession extends StandardSession {
  private static Logger log = Logger.getLogger("RedisSession");
  
  protected HashMap<String, Object> changedAttributes;
  
  public RedisSession(Manager manager) {
    super(manager);
    resetChangedAttributes();
  }
  
  public Boolean isDirty() {
    return !changedAttributes.isEmpty();
  }
  
  public HashMap<String, Object> getChangedAttributes() {
    return changedAttributes;
  }
  
  public void resetChangedAttributes() {
    changedAttributes = new HashMap<String, Object>();
  }
  
  public void setAttribute(String key, Object value) {
    Object oldValue = getAttribute(key);
    if ( value == null && oldValue != null
         || oldValue != null && value == null
         || !value.getClass().isInstance(oldValue)
         || !value.equals(oldValue) ) {
      changedAttributes.put(key, value);
    }
    
    super.setAttribute(key, value);
  }
  
}
