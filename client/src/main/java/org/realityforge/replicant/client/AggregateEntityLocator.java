package org.realityforge.replicant.client;

import java.util.ArrayList;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import static org.realityforge.braincheck.Guards.*;

/**
 * A basic EntityLocator implementation that allows explicit per-type registration
 */
public class AggregateEntityLocator
  implements EntityLocator
{
  private final ArrayList<EntityLocator> _entityLocators = new ArrayList<>();

  public AggregateEntityLocator( @Nonnull final EntityLocator... entityLocator )
  {
    for ( final EntityLocator locator : entityLocator )
    {
      registerEntityLocator( locator );
    }
  }

  protected final <T> void registerEntityLocator( @Nonnull final EntityLocator entityLocator )
  {
    apiInvariant( () -> !_entityLocators.contains( entityLocator ),
                  () -> "Attempting to register entityLocator " + entityLocator + " when already present." );
    _entityLocators.add( entityLocator );
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  @Override
  public final <T> T findByID( @Nonnull final Class<T> type, @Nonnull final Object id )
  {
    for ( final EntityLocator entityLocator : _entityLocators )
    {
      final T entity = entityLocator.findByID( type, id );
      if ( null != entity )
      {
        return entity;
      }
    }
    return null;
  }
}
