package blackboard.catalina.session;

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
public class KryoSerializer implements Serializer
{

  // Keep Kryo instances on the thread so that we aren't continually creating
  // them - they are expensive.  Might consider changing this to a true
  // pool at some point.
  private ThreadLocal<Kryo> kryo = new ThreadLocal<Kryo>() {

    @Override
    protected Kryo initialValue()
    {
      return createKryo();
    }

  };

  /**
   * Implements the creation of the Kryo object.  Override this to implement special
   * processing logic for creating a Kryo.
   * @return a new Kryo object.
   */
  protected Kryo createKryo()
  {
    return new Kryo();
  }

  public final byte[] writeSession( RedisSession session ) throws IOException
  {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    Output output = new Output(new BufferedOutputStream(bos));

    try
    {
      writeSession( session, kryo.get(), output );
    }
    finally
    {
      output.close();
    }

    return bos.toByteArray();
  }

  protected void writeSession( HttpSession session, Kryo kryo, Output output ) throws IOException
  {
    kryo.writeObject(output, (RedisSession) session);
  }

  public final RedisSession readSession( byte[] data, final RedisSessionFactory sessionFactory ) throws IOException, ClassNotFoundException
  {
    BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(data));
    Input input = new Input(bis);

    try
    {
      return readSession( sessionFactory, kryo.get(), input );
    }
    finally
    {
      input.close();
    }
  }

  protected RedisSession readSession( final RedisSessionFactory sessionFactory, Kryo kryo, Input input ) throws IOException, ClassNotFoundException
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
