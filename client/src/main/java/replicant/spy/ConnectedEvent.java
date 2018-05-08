package replicant.spy;

import arez.spy.SerializableEvent;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Notification when an DataLoader has connected from datasource.
 */
public final class ConnectedEvent
  implements SerializableEvent
{
  @Nonnull
  private final Class<?> _systemType;

  public ConnectedEvent( @Nonnull final Class<?> systemType )
  {
    _systemType = Objects.requireNonNull( systemType );
  }

  @Nonnull
  public Class<?> getSystemType()
  {
    return _systemType;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void toMap( @Nonnull final Map<String, Object> map )
  {
    map.put( "type", "DataLoader.Connect" );
    map.put( "systemType", getSystemType().getSimpleName() );
  }
}