package com.radiadesign.catalina.session;

import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * FactorySerializer extends the Serializer interface to allow for serializers that create their own instances.
 * By passing in a factory instead of an actual session, the serializer has more flexibility to create the session
 * on demand.
 */
public interface FactorySerializer extends Serializer
{

  RedisSession deserializeInto(byte[] data, RedisSessionFactory sessionFactory ) throws IOException, ClassNotFoundException;

}
