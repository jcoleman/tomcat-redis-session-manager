package com.radiadesign.catalina.session;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.shaded.org.objenesis.instantiator.ObjectInstantiator;
import org.junit.Test;

import java.io.IOException;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KryoSerializerTest
{

  @Test
  public void serializeFrom_writesSession() throws IOException
  {
    RedisSession session = mock( RedisSession.class );
    when( session.getCreationTime() ).thenReturn( 1L );

    Kryo kryo = mock( Kryo.class );
    Output output = mock( Output.class );

    KryoSerializer cut = new KryoSerializer();
    cut.serializeFrom( session, kryo, output );

    verify( kryo ).writeObject( output, session );
  }

  @Test
  public void deserializeInto_registersCustomInstantiator() throws IOException, ClassNotFoundException
  {
    RedisSession session = mock( RedisSession.class );

    Registration reg = mock( Registration.class );

    Kryo kryo = mock( Kryo.class );
    when( kryo.register( RedisSession.class ) ).thenReturn( reg );

    Input input = mock( Input.class );
    RedisSessionFactory factory = mock( RedisSessionFactory.class );

    KryoSerializer cut = new KryoSerializer();
    cut.deserializeInto( factory, kryo, input );

    verify( reg ).setInstantiator( any( ObjectInstantiator.class ) );
  }

  @Test
  public void deserializeInto_readsSession() throws IOException, ClassNotFoundException
  {
    RedisSession session = mock( RedisSession.class );

    Registration reg = mock( Registration.class );

    Kryo kryo = mock( Kryo.class );
    when( kryo.register( RedisSession.class ) ).thenReturn( reg );

    Input input = mock( Input.class );
    RedisSessionFactory factory = mock( RedisSessionFactory.class );

    KryoSerializer cut = new KryoSerializer();
    cut.deserializeInto( factory, kryo, input );

    verify( kryo ).readObject( input, RedisSession.class );
  }

}
