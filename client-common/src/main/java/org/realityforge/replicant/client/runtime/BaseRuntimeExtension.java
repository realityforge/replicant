package org.realityforge.replicant.client.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.realityforge.replicant.client.ChannelDescriptor;
import org.realityforge.replicant.client.EntityLocator;
import org.realityforge.replicant.client.EntitySubscriptionManager;
import org.realityforge.replicant.client.FilterUtil;

/**
 * Base interface which other runtime extensions extend.
 */
public interface BaseRuntimeExtension
{
  @Nonnull
  AreaOfInterestService getAreaOfInterestService();

  @Nonnull
  ContextConverger getContextConverger();

  @Nonnull
  EntityLocator getEntityLocator();

  @Nonnull
  EntitySubscriptionManager getSubscriptionManager();

  @Nonnull
  ReplicantClientSystem getReplicantClientSystem();

  /**
   * Create or update a Subscription
   */
  @Nonnull
  default SubscriptionReference subscribe( @Nonnull final Scope scope,
                                           @Nonnull final ChannelDescriptor channel,
                                           @Nullable final Object filter )
  {
    Subscription subscription = getAreaOfInterestService().findSubscription( channel );
    if ( null == subscription )
    {
      subscription = getAreaOfInterestService().createSubscription( channel, filter );
    }
    else if ( !FilterUtil.filtersEqual( subscription.getFilter(), filter ) )
    {
      getAreaOfInterestService().updateSubscription( subscription, filter );
    }
    final SubscriptionReference reference = scope.getSubscriptionReference( subscription );
    if ( null == reference )
    {
      return getAreaOfInterestService().createSubscriptionReference( scope, channel );
    }
    else
    {
      return reference;
    }
  }

  /**
   * Convert object subscription to root object (specified object Type+ID) to target graphs subscription
   * based on function. If the root object is not yet present then return an empty stream.
   * This is typically used by the function passed into the convergeCrossDataSourceSubscriptions() method.
   */
  @Nonnull
  default <T, O> Stream<O> instanceSubscriptionToValues( @Nonnull final Class<T> type,
                                                         @Nonnull final Object id,
                                                         @Nonnull final Function<T, Stream<O>> rootToStream )
  {
    final T root = getEntityLocator().findByID( type, id );
    return null != root ? rootToStream.apply( root ) : Stream.empty();
  }

  /**
   * Converge subscriptions across data sources.
   * All instances of the subscriptions to the source graph within the scope are collected.
   * The supplied function is used to generate a stream ofexpected subscriptions to the target graph
   * that are reachable from the source graphs. If an expected subscription is missing it is added,
   * if an additional subscription is present then it is released.
   */
  default void convergeCrossDataSourceSubscriptions( @Nonnull final Scope scope,
                                                     @Nonnull final Enum sourceGraph,
                                                     @Nonnull final Enum targetGraph,
                                                     @Nullable final Object filter,
                                                     @Nonnull final Function<Object, Stream<Object>> sourceIDToTargetIDs )
  {
    getContextConverger().pauseAndRun( () -> doConvergeCrossDataSourceSubscriptions( scope,
                                                                                     sourceGraph,
                                                                                     targetGraph,
                                                                                     filter,
                                                                                     sourceIDToTargetIDs ) );
  }

  /**
   * This is worker method for convergeCrossDataSourceSubscriptions. Do not use, do not override.
   */
  default void doConvergeCrossDataSourceSubscriptions( @Nonnull final Scope scope,
                                                       @Nonnull final Enum sourceGraph,
                                                       @Nonnull final Enum targetGraph,
                                                       @Nullable final Object filter,
                                                       @Nonnull final Function<Object, Stream<Object>> sourceIDToTargetIDs )
  {
    // Need to check both subscription and filters are identical.
    // If they are not the next step will either update the filters or add subscriptions
    final Map<Object, Subscription> existing =
      scope.getRequiredSubscriptionsByGraph( targetGraph ).stream().
        filter( subscription -> FilterUtil.filtersEqual( subscription.getFilter(), filter ) ).
        collect( Collectors.toMap( s -> s.getDescriptor().getID(), Function.identity() ) );

    //noinspection ConstantConditions
    scope.getRequiredSubscriptions().stream().
      filter( s -> s.getDescriptor().getGraph() == sourceGraph ).
      map( s -> s.getDescriptor().getID() ).
      flatMap( sourceIDToTargetIDs ).
      filter( Objects::nonNull ).
      filter( id -> null == existing.remove( id ) ).
      forEach( id -> subscribe( scope, new ChannelDescriptor( targetGraph, id ), filter ) );

    getSubscriptionManager().getInstanceSubscriptions( sourceGraph ).stream().
      flatMap( sourceIDToTargetIDs ).
      filter( Objects::nonNull ).
      filter( id -> null == existing.remove( id ) ).
      forEach( id -> subscribe( scope, new ChannelDescriptor( targetGraph, id ), filter ) );

    existing.values().forEach( Subscription::release );
  }
}
