package org.realityforge.replicant.client.subscription;

import arez.Disposable;
import arez.annotations.ArezComponent;
import arez.annotations.Feature;
import arez.annotations.Observable;
import arez.annotations.ObservableRef;
import arez.annotations.PreDispose;
import arez.component.ComponentObservable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.realityforge.braincheck.BrainCheckConfig;
import org.realityforge.replicant.client.Channel;
import org.realityforge.replicant.client.ChannelAddress;
import static org.realityforge.braincheck.Guards.*;

/**
 * Representation of a subscription to a channel.
 */
@ArezComponent
public abstract class Subscription
  implements Comparable<Subscription>
{
  private final Map<Class<?>, Map<Object, EntityEntry>> _entities = new HashMap<>();
  @Nonnull
  private final Channel _channel;

  public static Subscription create( @Nonnull final Channel channel )
  {
    return create( channel, true );
  }

  public static Subscription create( @Nonnull final Channel channel, final boolean explicitSubscription )
  {
    return new Arez_Subscription( channel, explicitSubscription );
  }

  Subscription( @Nonnull final Channel channel )
  {
    _channel = Objects.requireNonNull( channel );
  }

  @Nonnull
  public Channel getChannel()
  {
    return _channel;
  }

  @Observable( initializer = Feature.ENABLE )
  public abstract boolean isExplicitSubscription();

  public abstract void setExplicitSubscription( boolean explicitSubscription );

  @Observable( expectSetter = false )
  Map<Class<?>, Map<Object, EntityEntry>> getEntities()
  {
    return _entities;
  }

  @Nonnull
  public Collection<Class<?>> findAllEntityTypes()
  {
    return getEntities().keySet();
  }

  @Nonnull
  public List<Entity> findAllEntitiesByType( @Nonnull final Class<?> type )
  {
    final Map<Object, EntityEntry> typeMap = getEntities().get( type );
    return null == typeMap ?
           Collections.emptyList() :
           typeMap.values().stream().map( EntityEntry::getEntity ).collect( Collectors.toList() );
  }

  @Nullable
  public Entity findEntityByTypeAndId( @Nonnull final Class<?> type, @Nonnull final Object id )
  {
    final Map<Object, EntityEntry> typeMap = _entities.get( type );
    if ( null == typeMap )
    {
      getEntitiesObservable().reportObserved();
      return null;
    }
    else
    {
      final EntityEntry entry = typeMap.get( id );
      if ( null == entry )
      {
        getEntitiesObservable().reportObserved();
        return null;
      }
      else
      {
        ComponentObservable.observe( entry );
        return entry.getEntity();
      }
    }
  }

  @ObservableRef
  protected abstract arez.Observable getEntitiesObservable();

  @SuppressWarnings( "unchecked" )
  @Override
  public int compareTo( @NotNull final Subscription o )
  {
    return getChannel().getAddress().getChannelType().compareTo( o.getChannel().getAddress().getChannelType() );
  }

  final void linkSubscriptionToEntity( @Nonnull final Entity entity )
  {
    getEntitiesObservable().preReportChanged();
    final Class<?> type = entity.getType();
    final Object id = entity.getId();
    Map<Object, EntityEntry> typeMap = _entities.get( type );
    if ( null == typeMap )
    {
      typeMap = new HashMap<>();
      typeMap.put( id, EntityEntry.create( entity ) );
      _entities.put( type, typeMap );
      getEntitiesObservable().reportChanged();
    }
    else
    {
      if ( !typeMap.containsKey( id ) )
      {
        typeMap.put( id, EntityEntry.create( entity ) );
        getEntitiesObservable().reportChanged();
      }
    }
  }

  /**
   * Unlink the specified entity from this subscription.
   * This method does not delink channel from entity and it is assumed this is achieved through
   * other means such as {@link Entity#delinkSubscriptionFromEntity(Subscription)}.
   *
   * @param entity the entity.
   */
  final void delinkEntityFromSubscription( @Nonnull final Entity entity )
  {
    getEntitiesObservable().preReportChanged();
    final Class<?> entityType = entity.getType();
    final Map<Object, EntityEntry> typeMap = _entities.get( entityType );
    final ChannelAddress address = getChannel().getAddress();
    if ( BrainCheckConfig.checkInvariants() )
    {
      invariant( () -> null != typeMap,
                 () -> "Entity type " + entityType.getSimpleName() + " not present in subscription " +
                       "to channel " + address );
    }
    assert null != typeMap;
    final EntityEntry removed = typeMap.remove( entity.getId() );
    if ( BrainCheckConfig.checkInvariants() )
    {
      invariant( () -> null != removed,
                 () -> "Entity instance " + entity + " not present in subscription to channel " + address );
    }
    Disposable.dispose( removed );
    if ( typeMap.isEmpty() )
    {
      _entities.remove( entityType );
    }
    getEntitiesObservable().reportChanged();
  }

  @PreDispose
  final void preDispose()
  {
    delinkSubscriptionFromAllEntities();
  }

  private void delinkSubscriptionFromAllEntities()
  {
    _entities.values()
      .stream()
      .flatMap( entitySet -> entitySet.values().stream() )
      .forEachOrdered( entity -> entity.getEntity().delinkSubscriptionFromEntity( this ) );
  }
}
