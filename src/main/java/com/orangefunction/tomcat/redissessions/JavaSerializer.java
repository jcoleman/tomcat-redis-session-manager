package com.orangefunction.tomcat.redissessions;

import org.apache.catalina.util.CustomObjectInputStream;

import java.io.*;


public class JavaSerializer implements Serializer
{

  @Override
  public byte[] writeSession( RedisSession session ) throws IOException
  {
    RedisSession redisSession = (RedisSession) session;
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream( new BufferedOutputStream( bos ) );
    oos.writeLong( redisSession.getCreationTime() );
    redisSession.writeObjectData( oos );

    oos.close();

    return bos.toByteArray();
  }

  @Override
  public RedisSession readSession( byte[] data, RedisSessionFactory factory ) throws IOException, ClassNotFoundException
  {

    RedisSession redisSession = factory.createRedisSession();

    BufferedInputStream bis = new BufferedInputStream( new ByteArrayInputStream( data ) );
    ObjectInputStream ois = new CustomObjectInputStream( bis, factory.getSessionClassLoader() );
    redisSession.setCreationTime( ois.readLong() );
    redisSession.readObjectData( ois );

    return redisSession;
  }
}
