package replicant.spy;

import java.util.HashMap;
import org.realityforge.guiceyloops.shared.ValueUtil;
import org.testng.annotations.Test;
import replicant.AbstractReplicantTest;
import replicant.ChannelAddress;
import replicant.Subscription;
import static org.testng.Assert.*;

public class SubscriptionCreatedEventTest
  extends AbstractReplicantTest
{
  @Test
  public void basicOperation()
  {
    // Pause scheduler so Autoruns don't auto-converge
    pauseScheduler();

    final String filter = ValueUtil.randomString();
    final Subscription subscription = createSubscription( new ChannelAddress( 1, 2 ), filter, true );

    final SubscriptionCreatedEvent event = new SubscriptionCreatedEvent( subscription );

    assertEquals( event.getSubscription(), subscription );

    final HashMap<String, Object> data = new HashMap<>();
    safeAction( () -> event.toMap( data ) );

    assertEquals( data.get( "type" ), "Subscription.Created" );
    assertEquals( data.get( "channel.systemId" ), 1 );
    assertEquals( data.get( "channel.channelId" ), 2 );
    assertNull( data.get( "channel.id" ) );
    assertEquals( data.get( "channel.filter" ), filter );
    assertEquals( data.get( "explicitSubscription" ), true );
    assertEquals( data.size(), 6 );
  }
}
