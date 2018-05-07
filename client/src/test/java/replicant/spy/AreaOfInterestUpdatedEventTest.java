package replicant.spy;

import arez.Arez;
import java.util.HashMap;
import org.realityforge.guiceyloops.shared.ValueUtil;
import org.testng.annotations.Test;
import replicant.AbstractReplicantTest;
import replicant.AreaOfInterest;
import replicant.Channel;
import replicant.ChannelAddress;
import static org.testng.Assert.*;

public class AreaOfInterestUpdatedEventTest
  extends AbstractReplicantTest
{
  @Test
  public void basicOperation()
  {
    final ChannelAddress address = new ChannelAddress( G.G1 );
    final String filter = ValueUtil.randomString();
    final Channel channel = Channel.create( address, filter );
    final AreaOfInterest areaOfInterest = AreaOfInterest.create( channel );

    final AreaOfInterestUpdatedEvent event = new AreaOfInterestUpdatedEvent( areaOfInterest );

    assertEquals( event.getAreaOfInterest(), areaOfInterest );

    final HashMap<String, Object> data = new HashMap<>();
    Arez.context().safeAction( () -> event.toMap( data ) );

    assertEquals( data.get( "type" ), "AreaOfInterest.Updated" );
    assertEquals( data.get( "channel.type" ), "G1" );
    assertEquals( data.get( "channel.id" ), null );
    assertEquals( data.get( "channel.filter" ), filter );
    assertEquals( data.size(), 4 );
  }

  enum G
  {
    G1
  }
}
