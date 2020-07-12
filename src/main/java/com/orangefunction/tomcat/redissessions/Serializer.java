package com.orangefunction.tomcat.redissessions;

import java.io.IOException;

/**
 * <p>Title:        Godfather1103's Github</p>
 * <p>Copyright:    Copyright (c) 2020</p>
 * <p>Company:      https://github.com/godfather1103</p>
 *
 * @author 作者: Jack Chu E-mail: chuchuanbao@gmail.com
 * 创建时间：2020-07-01 17:20
 * @version 1.0
 * @since 1.0
 */
public interface Serializer {
    void setClassLoader(ClassLoader loader);

    byte[] attributesHashFrom(RedisSession session) throws IOException;

    byte[] serializeFrom(RedisSession session, SessionSerializationMetadata metadata) throws IOException;

    void deserializeInto(byte[] data, RedisSession session, SessionSerializationMetadata metadata) throws IOException, ClassNotFoundException;
}
