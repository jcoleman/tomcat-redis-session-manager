package com.radiadesign.catalina.session;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.shaded.org.objenesis.instantiator.ObjectInstantiator;

import javax.servlet.http.HttpSession;
import java.io.*;

/**
 * KryoSerializer implements the Serializer interface using the ultra-fast Kryo framework.
 */
public class KryoSerializer implements FactorySerializer
{

  // Keep Kryo instances on the thread so that we aren't continually creating
  // them - they are expensive.  Might consider changing this to a true
  // pool at some point.
  private ThreadLocal<Kryo> kryo = new ThreadLocal<Kryo>() {

    @Override
    protected Kryo initialValue()
    {
      return new Kryo();
    }

  };

  @Override
  public void setClassLoader( ClassLoader loader )
  {
    // NOOP - Don't need this
  }

  @Override
  public byte[] serializeFrom( HttpSession session ) throws IOException
  {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    Output output = new Output(new BufferedOutputStream(bos));

    try
    {
      serializeFrom( session, kryo.get(), output );
    }
    finally
    {
      output.close();
    }

    return bos.toByteArray();
  }

  protected void serializeFrom( HttpSession session, Kryo kryo, Output output ) throws IOException
  {
    kryo.writeObject(output, (RedisSession) session);
  }

  @Override
  public HttpSession deserializeInto( byte[] data, HttpSession session ) throws IOException, ClassNotFoundException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public RedisSession deserializeInto( byte[] data, final RedisSessionFactory sessionFactory ) throws IOException, ClassNotFoundException
  {
    BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(data));
    Input input = new Input(bis);

    try
    {
      return deserializeInto( sessionFactory, kryo.get(), input );
    }
    finally
    {
      input.close();
    }
  }

  protected RedisSession deserializeInto( final RedisSessionFactory sessionFactory, Kryo kryo, Input input ) throws IOException, ClassNotFoundException
  {
    Registration reg = kryo.register( RedisSession.class );
    reg.setInstantiator( new ObjectInstantiator()
    {
      @Override
      public Object newInstance()
      {
        return sessionFactory.createRedisSession();
      }
    } );

    return kryo.readObject( input, RedisSession.class );
  }

}
