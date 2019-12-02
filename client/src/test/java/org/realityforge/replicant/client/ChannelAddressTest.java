package org.realityforge.replicant.client;

import org.realityforge.replicant.client.transport.TestGraph;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ChannelAddressTest
{
  @SuppressWarnings( "EqualsWithItself" )
  @Test
  public void basicOperation()
  {
    final ChannelAddress descriptor1 = new ChannelAddress( TestGraph.A );
    final ChannelAddress descriptor2 = new ChannelAddress( TestGraph.B, 1 );

    assertEquals( descriptor1.getSystem(), TestGraph.class );
    assertEquals( descriptor1.getGraph(), TestGraph.A );
    assertNull( descriptor1.getID() );
    assertEquals( descriptor1.toString(), "TestGraph.A" );
    assertTrue( descriptor1.equals( descriptor1 ) );
    assertFalse( descriptor1.equals( descriptor2 ) );

    assertEquals( descriptor2.getSystem(), TestGraph.class );
    assertEquals( descriptor2.getGraph(), TestGraph.B );
    assertEquals( descriptor2.getID(), 1 );
    assertEquals( descriptor2.toString(), "TestGraph.B:1" );
    assertFalse( descriptor2.equals( descriptor1 ) );
    assertTrue( descriptor2.equals( descriptor2 ) );
  }
}
