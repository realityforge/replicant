package org.realityforge.replicant.server;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonObject;
import org.realityforge.replicant.server.ChannelAction.Action;
import org.realityforge.replicant.server.transport.Packet;
import org.realityforge.replicant.server.transport.ReplicantSession;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ChangeAccumulatorTest
{
  @Test
  public void basicOperation()
  {
    final ReplicantSession c = new ReplicantSession( null, "s1" );
    final ChangeAccumulator accumulator = new ChangeAccumulator();

    final String id = "myID";
    final int typeID = 42;

    final EntityMessage message = MessageTestUtil.createMessage( id, typeID, 0, "r1", "r2", "a1", "a2" );

    final int channelId = 1;
    final String subChannelId = "2";

    accumulator.addChange( c, new Change( message, channelId, subChannelId ) );
    final boolean impactsInitiator = accumulator.complete( "s1", "j1" );

    assertTrue( impactsInitiator );
    assertEquals( c.getQueue().size(), 1 );
    final Packet packet = c.getQueue().nextPacketToProcess();
    final Change change = packet.getChangeSet().getChanges().iterator().next();
    assertEquals( change.getId(), "42#myID" );
    assertEquals( change.getEntityMessage().getId(), id );
    assertEquals( change.getEntityMessage().getTypeId(), typeID );
    assertEquals( packet.getRequestId(), "j1" );
    final Map<Integer, Serializable> channels = change.getChannels();
    assertEquals( channels.size(), 1 );
    assertEquals( channels.get( channelId ), subChannelId );

    accumulator.complete( null, null );
    assertEquals( c.getQueue().size(), 1 );
  }

  @Test
  public void addEntityMessages()
  {
    final ReplicantSession c = new ReplicantSession( null, "s1" );
    final ChangeAccumulator accumulator = new ChangeAccumulator();

    final String id = "myID";
    final int typeID = 42;

    final EntityMessage message = MessageTestUtil.createMessage( id, typeID, 0, "r1", "r2", "a1", "a2" );

    accumulator.addChanges( c, Arrays.asList( new Change( message, 1, 0 ) ) );

    assertEquals( accumulator.getChangeSet( c ).getChanges().size(), 1 );

    final boolean impactsInitiator = accumulator.complete( "s1", "j1" );

    assertEquals( accumulator.getChangeSet( c ).getChanges().size(), 0 );

    assertTrue( impactsInitiator );
    assertEquals( c.getQueue().size(), 1 );
    final Packet packet = c.getQueue().nextPacketToProcess();
    assertEquals( packet.getChangeSet().getChanges().iterator().next().getEntityMessage().getId(), id );
    assertEquals( packet.getRequestId(), "j1" );

    accumulator.complete( null, null );
    assertEquals( c.getQueue().size(), 1 );
  }

  @Test
  public void addActions()
  {
    final ReplicantSession c = new ReplicantSession( null, "s1" );
    final ChangeAccumulator accumulator = new ChangeAccumulator();

    final JsonObject filter = Json.createBuilderFactory( null ).createObjectBuilder().build();
    accumulator.addActions( c,
                            Arrays.asList( new ChannelAction( new ChannelAddress( 1, 2 ), Action.ADD, filter ) ) );

    assertEquals( accumulator.getChangeSet( c ).getChannelActions().size(), 1 );

    final boolean impactsInitiator = accumulator.complete( "s1", "j1" );

    assertEquals( accumulator.getChangeSet( c ).getChannelActions().size(), 0 );

    assertTrue( impactsInitiator );
    assertEquals( c.getQueue().size(), 1 );
    final Packet packet = c.getQueue().nextPacketToProcess();
    final ChannelAction action = packet.getChangeSet().getChannelActions().iterator().next();
    assertEquals( action.getChannelAddress().getChannelId(), 1 );
    assertEquals( action.getAction(), Action.ADD );
    assertEquals( action.getFilter(), filter );
    assertEquals( packet.getRequestId(), "j1" );

    assertEquals( c.getQueue().size(), 1 );
    accumulator.complete( null, null );
    assertEquals( c.getQueue().size(), 1 );
  }

  @Test
  public void basicOperation_whereSessionIDDifferent()
  {
    final ReplicantSession c = new ReplicantSession( null, "s1" );
    final ChangeAccumulator accumulator = new ChangeAccumulator();

    final EntityMessage message = MessageTestUtil.createMessage( "myID", 42, 0, "r1", "r2", "a1", "a2" );

    accumulator.addChange( c, new Change( message, 1, 0 ) );
    final boolean impactsInitiator = accumulator.complete( "s2", "j1" );

    assertFalse( impactsInitiator );

    assertEquals( c.getQueue().size(), 1 );
    assertNull( c.getQueue().nextPacketToProcess().getRequestId() );
  }

  @Test
  public void basicOperation_whereNoMessagesSentToInitiator()
  {
    final ReplicantSession c = new ReplicantSession( null, "s1" );
    final ChangeAccumulator accumulator = new ChangeAccumulator();

    accumulator.getChangeSet( c );
    final boolean impactsInitiator = accumulator.complete( "s1", "j1" );

    assertFalse( impactsInitiator );

    assertEquals( c.getQueue().size(), 0 );
  }
}
