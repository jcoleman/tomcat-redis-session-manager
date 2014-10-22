package com.orangefunction.tomcat.redissessions;

import java.io.*;


public class SessionSerializationMetadata implements Serializable {

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

  private void writeObject(java.io.ObjectOutputStream out) throws IOException {
    out.writeInt(sessionAttributesHash.length);
    out.write(this.sessionAttributesHash);
  }

  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
    int hashLength = in.readInt();
    byte[] sessionAttributesHash = new byte[hashLength];
    in.read(sessionAttributesHash, 0, hashLength);
    this.sessionAttributesHash = sessionAttributesHash;
  }

  private void readObjectNoData() throws ObjectStreamException {
    this.sessionAttributesHash = new byte[0];
  }

}
