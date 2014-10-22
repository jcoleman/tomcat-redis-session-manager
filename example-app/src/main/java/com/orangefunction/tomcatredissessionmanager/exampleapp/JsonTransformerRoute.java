package com.orangefunction.tomcatredissessionmanager.exampleapp;

import com.google.gson.Gson;
import com.orangefunction.tomcat.redissessions.RedisSession;
import java.util.HashMap;
import java.util.Collections;
import spark.ResponseTransformerRoute;
import spark.Session;
import javax.servlet.http.HttpSession;

public abstract class JsonTransformerRoute extends ResponseTransformerRoute {

    private Gson gson = new Gson();

    protected JsonTransformerRoute(String path) {
      super(path);
    }

    protected JsonTransformerRoute(String path, String acceptType) {
      super(path, acceptType);
    }

    @Override
    public String render(Object jsonObject) {
      return gson.toJson(jsonObject);
    }

}