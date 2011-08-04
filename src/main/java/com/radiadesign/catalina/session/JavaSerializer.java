package com.radiadesign.catalina.session;

import org.apache.catalina.ha.session.DeltaSession;
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

    DeltaSession deltaSession = (DeltaSession) session;
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(bos));
    oos.writeLong(deltaSession.getCreationTime());
    deltaSession.writeObjectData(oos);

    oos.close();

    return bos.toByteArray();
  }

  @Override
  public HttpSession deserializeInto(byte[] data, HttpSession session) throws IOException, ClassNotFoundException {

    DeltaSession deltaSession = (DeltaSession) session;

    BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(data));

    ObjectInputStream ois = new CustomObjectInputStream(bis, loader);
    deltaSession.setCreationTime(ois.readLong());
    deltaSession.readObjectData(ois);

    return session;
  }
}
