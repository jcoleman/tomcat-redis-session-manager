package blackboard.catalina.session;

/**
 * Simple factory interface for creating RedisSessions.
 */
public interface RedisSessionFactory
{

  public RedisSession createRedisSession();

  public ClassLoader getSessionClassLoader();

}
