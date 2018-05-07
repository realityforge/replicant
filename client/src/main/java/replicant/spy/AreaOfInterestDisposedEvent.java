package replicant.spy;

import arez.spy.SerializableEvent;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import replicant.AreaOfInterest;

/**
 * Notification when an AreaOfInterest has been disposed.
 */
public final class AreaOfInterestDisposedEvent
  implements SerializableEvent
{
  @Nonnull
  private final AreaOfInterest _areaOfInterest;

  public AreaOfInterestDisposedEvent( @Nonnull final AreaOfInterest areaOfInterest )
  {
    _areaOfInterest = Objects.requireNonNull( areaOfInterest );
  }

  @Nonnull
  public AreaOfInterest getAreaOfInterest()
  {
    return _areaOfInterest;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void toMap( @Nonnull final Map<String, Object> map )
  {
    map.put( "type", "AreaOfInterest.Disposed" );
    map.put( "channel.type", getAreaOfInterest().getChannel().getAddress().getChannelType().name() );
    map.put( "channel.id", getAreaOfInterest().getChannel().getAddress().getId() );
    map.put( "channel.filter", getAreaOfInterest().getChannel().getFilter() );
  }
}
