package com.orangefunction.tomcat.redissessions;

import java.security.Principal;
import org.apache.catalina.Manager;
import org.apache.catalina.session.StandardSession;
import java.util.HashMap;
import java.io.IOException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


public class RedisSession extends StandardSession {

  private final Log log = LogFactory.getLog(RedisSession.class);

  protected static Boolean manualDirtyTrackingSupportEnabled = false;

  public static void setManualDirtyTrackingSupportEnabled(Boolean enabled) {
    manualDirtyTrackingSupportEnabled = enabled;
  }

  protected static String manualDirtyTrackingAttributeKey = "__changed__";

  public static void setManualDirtyTrackingAttributeKey(String key) {
    manualDirtyTrackingAttributeKey = key;
  }


  protected HashMap<String, Object> changedAttributes;
  protected Boolean dirty;

  public RedisSession(Manager manager) {
    super(manager);
    resetDirtyTracking();
  }

  public Boolean isDirty() {
    return dirty || !changedAttributes.isEmpty();
  }

  public HashMap<String, Object> getChangedAttributes() {
    return changedAttributes;
  }

  public void resetDirtyTracking() {
    changedAttributes = new HashMap<>();
    dirty = false;
  }

  @Override
  public void setAttribute(String key, Object value) {
    if (manualDirtyTrackingSupportEnabled && manualDirtyTrackingAttributeKey.equals(key)) {
      dirty = true;
      return;
    }

    Object oldValue = getAttribute(key);
    super.setAttribute(key, value);

    if ( (value != null || oldValue != null)
         && ( value == null && oldValue != null
              || oldValue == null && value != null
              || !value.getClass().isInstance(oldValue)
              || !value.equals(oldValue) ) ) {
      if (this.manager instanceof RedisSessionManager
          && ((RedisSessionManager)this.manager).getSaveOnChange()) {
        try {
          ((RedisSessionManager)this.manager).save(this, true);
        } catch (IOException ex) {
          log.error("Error saving session on setAttribute (triggered by saveOnChange=true): " + ex.getMessage());
        }
      } else {
        changedAttributes.put(key, value);
      }
    }
  }

  @Override
  public void removeAttribute(String name) {
    super.removeAttribute(name);
    if (this.manager instanceof RedisSessionManager
        && ((RedisSessionManager)this.manager).getSaveOnChange()) {
      try {
        ((RedisSessionManager)this.manager).save(this, true);
      } catch (IOException ex) {
        log.error("Error saving session on setAttribute (triggered by saveOnChange=true): " + ex.getMessage());
      }
    } else {
      dirty = true;
    }
  }

  @Override
  public void setId(String id) {
    // Specifically do not call super(): it's implementation does unexpected things
    // like calling manager.remove(session.id) and manager.add(session).

    this.id = id;
  }

  @Override
  public void setPrincipal(Principal principal) {
    dirty = true;
    super.setPrincipal(principal);
  }

  @Override
  public void writeObjectData(java.io.ObjectOutputStream out) throws IOException {
    super.writeObjectData(out);
    out.writeLong(this.getCreationTime());
  }

  @Override
  public void readObjectData(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
    super.readObjectData(in);
    this.setCreationTime(in.readLong());
  }

}
