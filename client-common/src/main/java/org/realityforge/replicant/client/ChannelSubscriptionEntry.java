package org.realityforge.replicant.client;

import arez.annotations.Action;
import arez.annotations.ArezComponent;
import arez.annotations.Feature;
import arez.annotations.Observable;
import arez.component.AbstractContainer;
import arez.component.RepositoryUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Representation of a subscription to a graph.
 */
@ArezComponent
public abstract class ChannelSubscriptionEntry
  extends AbstractContainer<Class<?>, Map<Object, EntitySubscriptionEntry>>
{
  @Nonnull
  private final Channel _channel;

  private final Map<Class<?>, Map<Object, EntitySubscriptionEntry>> _entities =
    new HashMap<>();

  public static ChannelSubscriptionEntry create( @Nonnull final Channel channel, final boolean explicitSubscription )
  {
    return new Arez_ChannelSubscriptionEntry( channel, explicitSubscription );
  }

  ChannelSubscriptionEntry( @Nonnull final Channel channel )
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

  @Nonnull
  public List<Map<Object, EntitySubscriptionEntry>> getEntitySubscriptionEntries()
  {
    return RepositoryUtil.asList( entities() );
  }

  public Map<Class<?>, Map<Object, EntitySubscriptionEntry>> getEntities()
  {
    return _entities;
  }

  final Map<Class<?>, Map<Object, EntitySubscriptionEntry>> getRwEntities()
  {
    return _entities;
  }
  /*


  @Action(
      name = "create_name"
  )
  @Nonnull
  public DaggerDisabledRepository create(@Nonnull final String name) {
    final Arez_DaggerDisabledRepository entity = new Arez_DaggerDisabledRepository(name);
    registerEntity( entity );
    return entity;
  }
  */
}
