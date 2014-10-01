package com.orangefunction.tomcat.redissessions;

import java.io.IOException;

public interface Serializer
{

  byte[] writeSession( RedisSession session ) throws IOException;

  RedisSession readSession( byte[] data, RedisSessionFactory sessionFactory ) throws IOException, ClassNotFoundException;

}
