package com.orangefunction.tomcat.redissessions;

import org.apache.catalina.util.CustomObjectInputStream;

import javax.servlet.http.HttpSession;
import java.io.*;


public class JavaSerializer implements Serializer {
  private ClassLoader loader;

  @Override
  public void setClassLoader(ClassLoader loader) {
    this.loader = loader;
  }

  @Override
  public byte[] serializeFrom(HttpSession session) throws IOException {

    RedisSession redisSession = (RedisSession) session;
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(bos))) {
      redisSession.writeObjectData(oos);
    }

    return bos.toByteArray();
  }

  @Override
  public HttpSession deserializeInto(byte[] data, HttpSession session) throws IOException, ClassNotFoundException {

    RedisSession redisSession = (RedisSession) session;
    try(
        BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(data));
        ObjectInputStream ois = new CustomObjectInputStream(bis, loader);
    ) {
        redisSession.readObjectData(ois);
        return session;
    }
  }
}
