package com.orangefunction.tomcat.redissessions;

import org.apache.catalina.util.CustomObjectInputStream;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.HashMap;

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
public class JavaSerializer implements Serializer {
    private ClassLoader loader;

    private final Log log = LogFactory.getLog(JavaSerializer.class);

    @Override
    public void setClassLoader(ClassLoader loader) {
        this.loader = loader;
    }

    @Override
    public byte[] attributesHashFrom(RedisSession session) throws IOException {
        HashMap<String, Object> attributes = new HashMap<String, Object>();
        for (Enumeration<String> enumerator = session.getAttributeNames(); enumerator.hasMoreElements(); ) {
            String key = enumerator.nextElement();
            attributes.put(key, session.getAttribute(key));
        }

        byte[] serialized = null;

        try (
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(bos));
        ) {
            oos.writeUnshared(attributes);
            oos.flush();
            serialized = bos.toByteArray();
        }

        MessageDigest digester = null;
        try {
            digester = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            log.error("Unable to get MessageDigest instance for MD5");
        }
        return digester.digest(serialized);
    }

    @Override
    public byte[] serializeFrom(RedisSession session, SessionSerializationMetadata metadata) throws IOException {
        byte[] serialized = null;

        try (
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(bos));
        ) {
            oos.writeObject(metadata);
            session.writeObjectData(oos);
            oos.flush();
            serialized = bos.toByteArray();
        }

        return serialized;
    }

    @Override
    public void deserializeInto(byte[] data, RedisSession session, SessionSerializationMetadata metadata) throws IOException, ClassNotFoundException {
        try (
                BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(data));
                ObjectInputStream ois = new CustomObjectInputStream(bis, loader);
        ) {
            SessionSerializationMetadata serializedMetadata = (SessionSerializationMetadata) ois.readObject();
            metadata.copyFieldsFrom(serializedMetadata);
            session.readObjectData(ois);
        }
    }
}
