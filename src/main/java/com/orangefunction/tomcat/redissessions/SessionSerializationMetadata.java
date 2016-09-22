package com.orangefunction.tomcat.redissessions;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.*;


public class SessionSerializationMetadata implements Serializable {

  private final Log log = LogFactory.getLog(SessionSerializationMetadata.class);

  private byte[] sessionAttributesHash;

  public SessionSerializationMetadata() {
    this.sessionAttributesHash = new byte[0];
  }

  public byte[] getSessionAttributesHash() {
    return sessionAttributesHash;
  }

  public void setSessionAttributesHash(byte[] sessionAttributesHash) {
    this.sessionAttributesHash = sessionAttributesHash;
  }

  public void copyFieldsFrom(SessionSerializationMetadata metadata) {
    this.setSessionAttributesHash(metadata.getSessionAttributesHash());
  }

  private void writeObject(java.io.ObjectOutputStream out) throws Exception {
    try{
      out.writeInt(sessionAttributesHash.length);
      out.write(this.sessionAttributesHash);
    }catch (Exception e){
      log.error(e);
      throw e;
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws Exception {
    try {
      int hashLength = in.readInt();
      byte[] sessionAttributesHash = new byte[hashLength];
      in.read(sessionAttributesHash, 0, hashLength);
      this.sessionAttributesHash = sessionAttributesHash;
    } catch (Exception e) {
      log.error(e);
      throw e;
    }
  }

  private void readObjectNoData() throws ObjectStreamException {
    this.sessionAttributesHash = new byte[0];
  }

}
