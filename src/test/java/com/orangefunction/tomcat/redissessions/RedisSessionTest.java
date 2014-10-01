package com.orangefunction.tomcat.redissessions;

import org.apache.catalina.Context;
import org.apache.catalina.Manager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.security.Principal;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith( MockitoJUnitRunner.class )
public class RedisSessionTest
{

  private RedisSession _session;

  @Before
  public void setUp()
  {
    Context ctx = mock( Context.class );
    Manager manager = mock( Manager.class );
    when( manager.getContainer() ).thenReturn( ctx );

    _session = new RedisSession( manager );
    _session.setId( "foo" );
    _session.setValid( true );
    _session.setCreationTime( System.currentTimeMillis() );
  }

  @Test
  public void setAttribute_setsDirtyFlag()
  {
    _session.setAttribute( "foo", "bar" );
    assertThat( _session.isDirty(), is( true ) );
  }


  @Test
  public void removeAttribute_setsDirtyFlag()
  {
    _session.removeAttribute( "foo" );
    assertThat( _session.isDirty(), is( true ) );
  }



  @Test
  public void getAttribute_neverSetsDirtyFlag()
  {
    _session.getAttribute( "foo" );
    assertThat( _session.isDirty(), is( false ) );
  }


  @Test
  public void setPrincipal_setsDirtyFlag()
  {
    _session.setPrincipal( new Principal()
    {
      @Override
      public String getName()
      {
        return "bar";
      }
    } );

    assertThat( _session.isDirty(), is( true ) );
  }

  @Test
  public void resetDirtyTracking_setsDirtyFalse()
  {
    _session.setAttribute( "foo", "bar" );
    assertThat( _session.isDirty(), is( true ) );

    _session.resetDirtyTracking();
    assertThat( _session.isDirty(), is( false ) );
  }


}
