package org.realityforge.replicant.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * A class that records the subscriptions to entity graphs.
 */
public class EntitySubscriptionManagerImpl
  implements EntitySubscriptionManager
{
  //Graph => InstanceID
  private final HashMap<Enum, Map<Object, GraphSubscriptionEntry>> _instanceSubscriptions = new HashMap<>();

  //Graph => Type
  private final HashMap<Enum, GraphSubscriptionEntry> _typeSubscriptions = new HashMap<>();

  // Entity map: Type => ID
  private final HashMap<Class<?>, Map<Object, EntitySubscriptionEntry>> _entityMapping = new HashMap<>();

  @Nonnull
  private final EntityRepository _repository;

  @Inject
  public EntitySubscriptionManagerImpl( @Nonnull final EntityRepository repository )
  {
    _repository = repository;
  }

  @Nonnull
  @Override
  public Set<Enum> getTypeSubscriptions()
  {
    return Collections.unmodifiableSet( _typeSubscriptions.keySet() );
  }

  @Nonnull
  @Override
  public Set<Enum> getInstanceSubscriptionKeys()
  {
    return Collections.unmodifiableSet( _instanceSubscriptions.keySet() );
  }

  @Nonnull
  @Override
  public Set<Object> getInstanceSubscriptions( @Nonnull final Enum graph )
  {
    final Map<Object, GraphSubscriptionEntry> map = _instanceSubscriptions.get( graph );
    if ( null == map )
    {
      return Collections.emptySet();
    }
    else
    {
      return Collections.unmodifiableSet( map.keySet() );
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nonnull
  public final GraphSubscriptionEntry subscribe( @Nonnull final Enum graph,
                                                 @Nullable final Object filter )
    throws IllegalStateException
  {
    GraphSubscriptionEntry typeMap = findSubscription( graph );
    if ( null == typeMap )
    {
      final GraphSubscriptionEntry entry = new GraphSubscriptionEntry( new GraphDescriptor( graph, null ), filter );
      _typeSubscriptions.put( graph, entry );
      return entry;
    }
    else
    {
      throw new IllegalStateException( "Graph already subscribed: " + graph );
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nonnull
  public final GraphSubscriptionEntry subscribe( @Nonnull final Enum graph,
                                                 @Nonnull final Object id,
                                                 @Nullable final Object filter )
  {
    Map<Object, GraphSubscriptionEntry> instanceMap = _instanceSubscriptions.get( graph );
    if ( null == instanceMap )
    {
      instanceMap = new HashMap<>();
      _instanceSubscriptions.put( graph, instanceMap );
    }
    if ( !instanceMap.containsKey( id ) )
    {
      final GraphSubscriptionEntry entry = new GraphSubscriptionEntry( new GraphDescriptor( graph, id ), filter );
      instanceMap.put( id, entry );
      return entry;
    }
    else
    {
      throw new IllegalStateException( "Graph already subscribed: " + graph + ":" + id );
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nonnull
  public GraphSubscriptionEntry updateSubscription( @Nonnull final Enum graph, @Nonnull final Object filter )
    throws IllegalStateException
  {
    final GraphSubscriptionEntry subscription = getSubscription( graph );
    subscription.setFilter( filter );
    return subscription;
  }

  @Nonnull
  @Override
  public GraphSubscriptionEntry updateSubscription( @Nonnull final Enum graph,
                                                    @Nonnull final Object id,
                                                    @Nonnull final Object filter )
  {
    final GraphSubscriptionEntry subscription = getSubscription( graph, id );
    subscription.setFilter( filter );
    return subscription;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nullable
  public final GraphSubscriptionEntry findSubscription( @Nonnull final Enum graph )
  {
    return _typeSubscriptions.get( graph );
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nullable
  public final GraphSubscriptionEntry findSubscription( @Nonnull final Enum graph, @Nonnull final Object id )
  {
    Map<Object, GraphSubscriptionEntry> instanceMap = _instanceSubscriptions.get( graph );
    if ( null == instanceMap )
    {
      return null;
    }
    else
    {
      return instanceMap.get( id );
    }
  }

  /**
   * {@inheritDoc}
   */
  @Nonnull
  @Override
  public GraphSubscriptionEntry getSubscription( @Nonnull final Enum graph )
    throws IllegalArgumentException
  {
    final GraphSubscriptionEntry subscription = findSubscription( graph );
    if ( null == subscription )
    {
      throw new IllegalStateException( "Graph not subscribed: " + graph );
    }
    return subscription;
  }

  /**
   * {@inheritDoc}
   */
  @Nonnull
  @Override
  public GraphSubscriptionEntry getSubscription( @Nonnull final Enum graph, @Nonnull final Object id )
    throws IllegalArgumentException
  {
    final GraphSubscriptionEntry subscription = findSubscription( graph, id );
    if ( null == subscription )
    {
      throw new IllegalStateException( "Graph not subscribed: " + graph + "/" + id );
    }
    return subscription;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void unsubscribe( @Nonnull final Enum graph )
    throws IllegalStateException
  {
    final GraphSubscriptionEntry entry = _typeSubscriptions.remove( graph );
    if ( null == entry )
    {
      throw new IllegalStateException( "Graph not subscribed: " + graph );
    }
    deregisterUnOwnedEntities( entry );
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void unsubscribe( @Nonnull final Enum graph, @Nonnull final Object id )
    throws IllegalStateException
  {
    final Map<Object, GraphSubscriptionEntry> instanceMap = _instanceSubscriptions.get( graph );
    if ( null == instanceMap )
    {
      throw new IllegalStateException( "Graph not subscribed: " + graph + "/" + id );
    }
    final GraphSubscriptionEntry entry = instanceMap.remove( id );
    if ( null == entry )
    {
      throw new IllegalStateException( "Graph not subscribed: " + graph + "/" + id );
    }
    //TODO: Consider moving this method to DataLoaderService and having unsubscribe return entry
    deregisterUnOwnedEntities( entry );
  }

  private void deregisterUnOwnedEntities( final GraphSubscriptionEntry entry )
  {
    for ( final Entry<Class<?>, Map<Object, EntitySubscriptionEntry>> entitySet : entry.getEntities().entrySet() )
    {
      final Class<?> type = entitySet.getKey();
      for ( Entry<Object, EntitySubscriptionEntry> entityEntry : entitySet.getValue().entrySet() )
      {
        final Object entityID = entityEntry.getKey();
        final EntitySubscriptionEntry entitySubscription = entityEntry.getValue();
        final Map<GraphDescriptor, GraphSubscriptionEntry> graphSubscriptions =
          entitySubscription.getRwGraphSubscriptions();
        final GraphSubscriptionEntry element = graphSubscriptions.remove( entry.getDescriptor() );
        if ( null != element && 0 == graphSubscriptions.size() )
        {
          _repository.deregisterEntity( type, entityID );
        }
      }
    }
  }

  @Nonnull
  @Override
  public EntitySubscriptionEntry getSubscription( @Nonnull final Class<?> type, @Nonnull final Object id )
  {
    final EntitySubscriptionEntry entityEntry = findSubscription( type, id );
    if ( null == entityEntry )
    {
      throw new IllegalStateException( "Entity not subscribed: " + type.getSimpleName() + "/" + id );
    }
    return entityEntry;
  }

  @Nullable
  @Override
  public EntitySubscriptionEntry findSubscription( @Nonnull final Class<?> type, @Nonnull final Object id )
  {
    return getEntityTypeMap( type ).get( id );
  }

  @Override
  public void updateEntity( @Nonnull final Class<?> type,
                            @Nonnull final Object id,
                            @Nonnull final GraphDescriptor[] graphs )
  {
    final EntitySubscriptionEntry entityEntry = getEntitySubscriptions( type, id );
    for ( final GraphDescriptor graph : graphs )
    {
      GraphSubscriptionEntry entry = entityEntry.getRwGraphSubscriptions().get( graph );
      if ( null == entry )
      {
        final Enum g = graph.getGraph();
        entry = null == graph.getID() ? getSubscription( g ) : getSubscription( g, graph.getID() );
        entityEntry.getRwGraphSubscriptions().put( graph, entry );
      }
      Map<Object, EntitySubscriptionEntry> typeMap = entry.getEntities().get( type );
      if ( null == typeMap )
      {
        typeMap = new HashMap<>();
        entry.getRwEntities().put( type, typeMap );
      }
      if ( !typeMap.containsKey( id ) )
      {
        typeMap.put( id, entityEntry );
      }
    }
  }

  @Nonnull
  @Override
  public EntitySubscriptionEntry removeEntityFromGraph( @Nonnull final Class<?> type,
                                                        @Nonnull final Object id,
                                                        @Nonnull final GraphDescriptor graph )
    throws IllegalStateException
  {
    final EntitySubscriptionEntry entry = getEntitySubscriptions( type, id );
    final Map<GraphDescriptor, GraphSubscriptionEntry> subscriptions = entry.getRwGraphSubscriptions();
    final GraphSubscriptionEntry graphEntry = subscriptions.remove( graph );
    if( null == graphEntry )
    {
      final String message = "Unable to locate graph " + graph + " for entity " + type.getSimpleName() + "/" + id;
      throw  new IllegalStateException( message );
    }
    removeEntityFromGraph( type, id, graphEntry );
    return entry;
  }

  @Override
  public void removeEntity( @Nonnull final Class<?> type, @Nonnull final Object id )
  {
    final Map<Object, EntitySubscriptionEntry> typeMap = _entityMapping.get( type );
    if ( null == typeMap )
    {
      return;
    }
    final EntitySubscriptionEntry entityEntry = typeMap.remove( id );
    if ( null == entityEntry )
    {
      return;
    }
    for ( final GraphSubscriptionEntry entry : entityEntry.getRwGraphSubscriptions().values() )
    {
      removeEntityFromGraph( type, id, entry );
    }
  }

  private void removeEntityFromGraph( final Class<?> type, final Object id, final GraphSubscriptionEntry entry )
  {
    final EntitySubscriptionEntry removed = entry.getRwEntities().get( type ).remove( id );
    if ( null == removed )
    {
      final String message =
        "Unable to remove entity " + type.getSimpleName() + "/" + id + " from " + entry.getDescriptor();
      throw new IllegalStateException( message );
    }
  }

  private EntitySubscriptionEntry getEntitySubscriptions( final Class<?> type, final Object id )
  {
    final Map<Object, EntitySubscriptionEntry> typeMap = getEntityTypeMap( type );
    EntitySubscriptionEntry entityEntry = typeMap.get( id );
    if ( null == entityEntry )
    {
      entityEntry = new EntitySubscriptionEntry( type, id );
      typeMap.put( id, entityEntry );
    }
    return entityEntry;
  }

  @Nonnull
  private Map<Object, EntitySubscriptionEntry> getEntityTypeMap( @Nonnull final Class<?> type )
  {
    Map<Object, EntitySubscriptionEntry> typeMap = _entityMapping.get( type );
    if ( null == typeMap )
    {
      typeMap = new HashMap<>();
      _entityMapping.put( type, typeMap );
    }
    return typeMap;
  }
}
