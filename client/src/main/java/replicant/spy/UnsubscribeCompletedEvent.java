package replicant.spy;

import arez.spy.SerializableEvent;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import replicant.ChannelAddress;

/**
 * Notification when a Connector completes an unsubscribe from a channel.
 */
public final class UnsubscribeCompletedEvent
  implements SerializableEvent
{
  private final int _schemaId;
  @Nonnull
  private final String _schemaName;
  @Nonnull
  private final ChannelAddress _address;

  public UnsubscribeCompletedEvent( final int schemaId,
                                    @Nonnull final String schemaName,
                                    @Nonnull final ChannelAddress address )
  {
    _schemaId = schemaId;
    _schemaName = Objects.requireNonNull( schemaName );
    _address = Objects.requireNonNull( address );
  }

  public int getSchemaId()
  {
    return _schemaId;
  }

  @Nonnull
  public String getSchemaName()
  {
    return _schemaName;
  }

  @Nonnull
  public ChannelAddress getAddress()
  {
    return _address;
  }

  @Override
  public void toMap( @Nonnull final Map<String, Object> map )
  {
    map.put( "type", "Connector.UnsubscribeCompleted" );
    map.put( "schema.id", getSchemaId() );
    map.put( "schema.name", getSchemaName() );
    final ChannelAddress address = getAddress();
    map.put( "channel.systemId", address.getSystemId() );
    map.put( "channel.channelId", address.getChannelId() );
    map.put( "channel.id", address.getId() );
  }
}
