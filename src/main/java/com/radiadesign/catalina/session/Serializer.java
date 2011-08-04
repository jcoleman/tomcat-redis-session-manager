package com.radiadesign.catalina.session;

import javax.servlet.http.HttpSession;
import java.io.IOException;

public interface Serializer {
  void setClassLoader(ClassLoader loader);

  byte[] serializeFrom(HttpSession session) throws IOException;

  HttpSession deserializeInto(byte[] data, HttpSession session) throws IOException, ClassNotFoundException;
}
