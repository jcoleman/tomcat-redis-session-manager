package com.orangefunction.tomcatredissessionmanager.exampleapp;

import com.google.gson.Gson;
import com.orangefunction.tomcat.redissessions.RedisSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import spark.ResponseTransformerRoute;
import spark.Session;
import javax.servlet.http.HttpSession;

public abstract class SessionJsonTransformerRoute extends ResponseTransformerRoute {

    private Gson gson = new Gson();

    protected SessionJsonTransformerRoute(String path) {
      super(path);
    }

    protected SessionJsonTransformerRoute(String path, String acceptType) {
      super(path, acceptType);
    }

    @Override
    public String render(Object object) {
      if (object instanceof Object[]) {
        Object[] tuple = (Object[])object;
        Session sparkSession = (Session)tuple[0];
        HttpSession session = (HttpSession)(sparkSession).raw();
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.putAll((Map<String, Object>)tuple[1]);
        map.put("sessionId", session.getId());
        return gson.toJson(map);
      } else if (object instanceof Session) {
        Session sparkSession = (Session)object;
        HashMap<String, Object> sessionMap = new HashMap<String, Object>();
        if (null != sparkSession) {
          HttpSession session = (HttpSession)(sparkSession).raw();
          sessionMap.put("sessionId", session.getId());
          HashMap<String, Object> attributesMap = new HashMap<String, Object>();
          for (String key : Collections.list(session.getAttributeNames())) {
            attributesMap.put(key, session.getAttribute(key));
          }
          sessionMap.put("attributes", attributesMap);
        }
        return gson.toJson(sessionMap);
      } else {
        return "{}";
      }
    }

}