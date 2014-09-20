package com.orangefunction.tomcatredissessionmanager.exampleapp;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import static spark.Spark.*;
import spark.*;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;
import com.radiadesign.catalina.session.*;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.session.StandardSessionFacade;
import org.apache.catalina.core.ApplicationContextFacade;
import org.apache.catalina.core.ApplicationContext;
import org.apache.catalina.core.StandardContext;
import java.lang.reflect.Field;
import javax.servlet.*;

public class WebApp implements spark.servlet.SparkApplication {

  protected String redisHost = "localhost";
  protected int redisPort = 6379;
  protected int redisDatabase = 0;
  protected String redisPassword = null;
  protected int redisTimeout = Protocol.DEFAULT_TIMEOUT;
  protected JedisPool redisConnectionPool;

  private void initializeJedisConnectionPool() {
    try {
      // TODO: Allow configuration of pool (such as size...)
      redisConnectionPool = new JedisPool(new JedisPoolConfig(), redisHost, redisPort, redisTimeout, redisPassword);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  protected Jedis acquireConnection() {
    if (null == redisConnectionPool) {
      initializeJedisConnectionPool();
    }
    Jedis jedis = redisConnectionPool.getResource();

    if (redisDatabase != 0) {
      jedis.select(redisDatabase);
    }

    return jedis;
  }

  protected void returnConnection(Jedis jedis, Boolean error) {
    if (error) {
      redisConnectionPool.returnBrokenResource(jedis);
    } else {
      redisConnectionPool.returnResource(jedis);
    }
  }

  protected void returnConnection(Jedis jedis) {
    returnConnection(jedis, false);
  }

  protected RedisSessionManager getRedisSessionManager(Request request) {
    RedisSessionManager sessionManager = null;
    ApplicationContextFacade appContextFacadeObj = (ApplicationContextFacade)request.session().raw().getServletContext();
    try {
      Field applicationContextField = appContextFacadeObj.getClass().getDeclaredField("context");
      applicationContextField.setAccessible(true);
      ApplicationContext appContextObj = (ApplicationContext)applicationContextField.get(appContextFacadeObj);
      Field standardContextField = appContextObj.getClass().getDeclaredField("context");
      standardContextField.setAccessible(true);
      StandardContext standardContextObj = (StandardContext)standardContextField.get(appContextObj);
      sessionManager = (RedisSessionManager)standardContextObj.getManager();
    } catch (Exception e) { }
    return sessionManager;
  }

  public void init() {

      // /session

      get(new SessionJsonTransformerRoute("/session", "application/json") {
         @Override
         public Object handle(Request request, Response response) {
            return request.session(false);
         }
      });

      put(new SessionJsonTransformerRoute("/session", "application/json") {
         @Override
         public Object handle(Request request, Response response) {
           Session session = request.session();
           for (String key : request.queryParams()) {
             session.attribute(key, request.queryParams(key));
           }
           return session;
         }
      });

      post(new SessionJsonTransformerRoute("/session", "application/json") {
         @Override
         public Object handle(Request request, Response response) {
           Session session = request.session();
           for (String key : request.queryParams()) {
             session.attribute(key, request.queryParams(key));
           }
           return session;
         }
      });

      delete(new SessionJsonTransformerRoute("/session", "application/json") {
         @Override
         public Object handle(Request request, Response response) {
           request.session().raw().invalidate();
           return null;
         }
      });


      // /session/attributes

      get(new SessionJsonTransformerRoute("/session/attributes", "application/json") {
         @Override
         public Object handle(Request request, Response response) {
           HashMap<String, Object> map = new HashMap<String, Object>();
           map.put("keys", request.session().attributes());
           return new Object[]{request.session(), map};
         }
      });

      get(new SessionJsonTransformerRoute("/session/attributes/:key", "application/json") {
         @Override
         public Object handle(Request request, Response response) {
           String key = request.params(":key");
           HashMap<String, Object> map = new HashMap<String, Object>();
           map.put("key", key);
           map.put("value", request.session().attribute(key));
           return new Object[]{request.session(), map};
         }
      });

      post(new SessionJsonTransformerRoute("/session/attributes/:key", "application/json") {
         @Override
         public Object handle(Request request, Response response) {
           String key = request.params(":key");
           String oldValue = request.session().attribute(key);
           request.session().attribute(key, request.queryParams("value"));
           HashMap<String, Object> map = new HashMap<String, Object>();
           map.put("key", key);
           map.put("value", request.session().attribute(key));
           map.put("oldValue", oldValue);
           if (null != request.queryParams("sleep")) {
             try {
               java.lang.Thread.sleep(Integer.parseInt(request.queryParams("sleep")));
             } catch (InterruptedException e) {}
           }
           return new Object[]{request.session(), map};
         }
      });

      delete(new SessionJsonTransformerRoute("/session/attributes/:key", "application/json") {
         @Override
         public Object handle(Request request, Response response) {
           String key = request.params(":key");
           String oldValue = request.session().attribute(key);
           request.session().raw().removeAttribute(key);
           HashMap<String, Object> map = new HashMap<String, Object>();
           map.put("key", key);
           map.put("value", request.session().attribute(key));
           map.put("oldValue", oldValue);
           return new Object[]{request.session(), map};
         }
      });


      // /sessions

      get(new JsonTransformerRoute("/sessions", "application/json") {
         @Override
         public Object handle(Request request, Response response) {
           Jedis jedis = null;
           Boolean error = true;
           try {
             jedis = acquireConnection();
             Set<String> keySet = jedis.keys("*");
             error = false;
             return keySet.toArray(new String[keySet.size()]);
           } finally {
             if (jedis != null) {
               returnConnection(jedis, error);
             }
           }
         }
      });

      delete(new JsonTransformerRoute("/sessions", "application/json") {
         @Override
         public Object handle(Request request, Response response) {
           Jedis jedis = null;
           Boolean error = true;
           try {
             jedis = acquireConnection();
             jedis.flushDB();
             Set<String> keySet = jedis.keys("*");
             error = false;
             return keySet.toArray(new String[keySet.size()]);
           } finally {
             if (jedis != null) {
               returnConnection(jedis, error);
             }
           }
         }
      });


      // /settings

      get(new SessionJsonTransformerRoute("/settings/:key", "application/json") {
         @Override
         public Object handle(Request request, Response response) {
           String key = request.params(":key");
           HashMap<String, Object> map = new HashMap<String, Object>();
           map.put("key", key);

           RedisSessionManager manager = getRedisSessionManager(request);
           if (null != manager) {
             if (key.equals("saveOnChange")) {
               map.put("value", new Boolean(manager.getSaveOnChange()));
             } else if (key.equals("maxInactiveInterval")) {
               map.put("value", new Integer(manager.getMaxInactiveInterval()));
             }
           } else {
             map.put("error", new Boolean(true));
           }

           return new Object[]{request.session(), map};
         }
      });

      post(new SessionJsonTransformerRoute("/settings/:key", "application/json") {
         @Override
         public Object handle(Request request, Response response) {
           String key = request.params(":key");
           String value = request.queryParams("value");
           HashMap<String, Object> map = new HashMap<String, Object>();
           map.put("key", key);

           RedisSessionManager manager = getRedisSessionManager(request);
           if (null != manager) {
             if (key.equals("saveOnChange")) {
               manager.setSaveOnChange(Boolean.parseBoolean(value));
               map.put("value", new Boolean(manager.getSaveOnChange()));
             } else if (key.equals("maxInactiveInterval")) {
               manager.setMaxInactiveInterval(Integer.parseInt(value));
               map.put("value", new Integer(manager.getMaxInactiveInterval()));
             }
           } else {
             map.put("error", new Boolean(true));
           }

           return new Object[]{request.session(), map};
         }
      });

   }

}
