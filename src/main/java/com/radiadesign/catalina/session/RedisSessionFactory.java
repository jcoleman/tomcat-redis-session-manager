package com.radiadesign.catalina.session;

/**
 * Simple factory interface for creating RedisSessions.
 */
public interface RedisSessionFactory
{

  public RedisSession createRedisSession();

}
