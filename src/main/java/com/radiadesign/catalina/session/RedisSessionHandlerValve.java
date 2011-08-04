package com.radiadesign.catalina.session;

import org.apache.catalina.Session;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Logger;


public class RedisSessionHandlerValve extends ValveBase {
  private static Logger log = Logger.getLogger("RedisSessionHandlerValve");
  private RedisSessionManager manager;

  public void setRedisSessionManager(RedisSessionManager manager) {
    this.manager = manager;
  }

  @Override
  public void invoke(Request request, Response response) throws IOException, ServletException {
    try {
      getNext().invoke(request, response);
    } finally {
      storeSession(request, response);
    }
  }

  private void storeSession(Request request, Response response) throws IOException {
    final Session session = request.getSessionInternal(false);

    if (session != null) {
      if (session.isValid()) {
        log.fine("Request with session completed, saving session " + session.getId());
        if (session.getSession() != null) {
          log.fine("HTTP Session present, saving " + session.getId());
          manager.save(session);
        } else {
          log.fine("No HTTP Session present, Not saving " + session.getId());
        }
      } else {
        log.fine("HTTP Session has been invalidated, removing :" + session.getId());
        manager.remove(session);
      }
    }
  }
}
