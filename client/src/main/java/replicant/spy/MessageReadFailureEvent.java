package replicant.spy;

import arez.spy.SerializableEvent;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Notification when a Connector generated an error attempting to read a message from a DataSource.
 */
public final class MessageReadFailureEvent
  implements SerializableEvent
{
  private final int _schemaId;
  @Nonnull
  private final String _schemaName;

  public MessageReadFailureEvent( final int schemaId, @Nonnull final String schemaName )
  {
    _schemaId = schemaId;
    _schemaName = Objects.requireNonNull( schemaName );
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

  @Override
  public void toMap( @Nonnull final Map<String, Object> map )
  {
    map.put( "type", "Connector.MessageReadFailure" );
    map.put( "schema.id", getSchemaId() );
    map.put( "schema.name", getSchemaName() );
  }
}
