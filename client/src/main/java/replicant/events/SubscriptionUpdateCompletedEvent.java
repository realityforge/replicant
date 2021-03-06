package replicant.events;

import java.util.Objects;
import javax.annotation.Nonnull;
import replicant.ChannelAddress;

/**
 * Notification when a Connector completes an update of the filter of a subscription.
 */
public final class SubscriptionUpdateCompletedEvent
{
  private final int _schemaId;
  @Nonnull
  private final ChannelAddress _address;

  public SubscriptionUpdateCompletedEvent( final int schemaId, @Nonnull final ChannelAddress address )
  {
    _schemaId = schemaId;
    _address = Objects.requireNonNull( address );
  }

  public int getSchemaId()
  {
    return _schemaId;
  }

  @Nonnull
  public ChannelAddress getAddress()
  {
    return _address;
  }
}
