package org.realityforge.replicant.client.transport;

import arez.Disposable;
import arez.annotations.Action;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.realityforge.replicant.client.Verifiable;
import replicant.AreaOfInterestAction;
import replicant.AreaOfInterestRequest;
import replicant.ChangeSet;
import replicant.ChannelAddress;
import replicant.ChannelChange;
import replicant.Connection;
import replicant.Connector;
import replicant.Entity;
import replicant.EntityChange;
import replicant.Linkable;
import replicant.MessageResponse;
import replicant.Replicant;
import replicant.ReplicantContext;
import replicant.RequestEntry;
import replicant.SafeProcedure;
import replicant.Subscription;
import replicant.spi.CacheEntry;
import replicant.spi.CacheService;
import replicant.spy.DataLoadStatus;
import static org.realityforge.braincheck.Guards.*;

/**
 * Class from which to extend to implement a service that loads data from a change set.
 * Data is loaded incrementally and the load can be broken up into several
 * steps to avoid locking a thread such as in GWT.
 */
@SuppressWarnings( { "WeakerAccess", "unused" } )
public abstract class AbstractDataLoaderService
  extends Connector
{
  protected static final Logger LOG = Logger.getLogger( AbstractDataLoaderService.class.getName() );

  private static final int DEFAULT_CHANGES_TO_PROCESS_PER_TICK = 100;
  private static final int DEFAULT_LINKS_TO_PROCESS_PER_TICK = 100;

  private int _changesToProcessPerTick = DEFAULT_CHANGES_TO_PROCESS_PER_TICK;
  private int _linksToProcessPerTick = DEFAULT_LINKS_TO_PROCESS_PER_TICK;
  /**
   * Flag indicating that the Connectors internal scheduler is actively progressing
   * requests and responses.
   */
  private boolean _schedulerActive;
  /**
   * Action invoked after current action completes to reset connection state.
   */
  private Runnable _resetAction;

  private Disposable _schedulerLock;

  protected AbstractDataLoaderService( @Nullable final ReplicantContext context,
                                       @Nonnull final Class<?> systemType )
  {
    super( context, systemType );
  }

  @Override
  public final void requestSubscribe( @Nonnull final ChannelAddress address, @Nullable final Object filter )
  {
    //TODO: Send spy message ..
    ensureConnection().requestSubscribe( address, filter );
    triggerScheduler();
  }

  @Override
  public final void requestSubscriptionUpdate( @Nonnull final ChannelAddress address,
                                               @Nullable final Object filter )
  {
    //TODO: Send spy message ..
    ensureConnection().requestSubscriptionUpdate( address, filter );
    triggerScheduler();
  }

  @Override
  public final void requestUnsubscribe( @Nonnull final ChannelAddress address )
  {
    //TODO: Send spy message ..
    ensureConnection().requestUnsubscribe( address );
    triggerScheduler();
  }

  /**
   * A symbolic key for describing system.
   */
  @Nonnull
  protected String getKey()
  {
    return getSessionContext().getKey();
  }

  @Nonnull
  protected abstract SessionContext getSessionContext();

  @Nonnull
  protected abstract ChangeMapper getChangeMapper();

  protected void onConnection( @Nonnull final String connectionId, @Nonnull final SafeProcedure action )
  {
    setConnection( new Connection( this, connectionId ), action );
    triggerScheduler();
  }

  protected void onDisconnection( @Nonnull final SafeProcedure action )
  {
    setConnection( null, action );
  }

  private void setConnection( @Nullable final Connection connection, @Nonnull final SafeProcedure action )
  {
    final Runnable runnable = () -> doSetConnection( connection, action );
    if ( null == connection || null == connection.getCurrentMessageResponse() )
    {
      runnable.run();
    }
    else
    {
      _resetAction = runnable;
    }
  }

  protected void doSetConnection( @Nullable final Connection connection, @Nonnull final SafeProcedure action )
  {
    if ( connection != getConnection() )
    {
      setConnection( connection );
      // This should probably be moved elsewhere ... but where?
      getSessionContext().setConnection( connection );
    }
    action.call();
  }

  /**
   * Schedule data loads using incremental scheduler.
   */
  final void triggerScheduler()
  {
    if ( !_schedulerActive )
    {
      _schedulerActive = true;

      doActivateScheduler();
    }
  }

  /**
   * Perform a single step progressing requests and responses.
   * This is invoked from the scheduler
   *
   * @return true if more work is to be done.
   */
  protected boolean scheduleTick()
  {
    if ( null == _schedulerLock )
    {
      _schedulerLock = context().pauseScheduler();
    }
    try
    {
      _schedulerActive = progressAreaOfInterestRequestProcessing() || progressResponseProcessing();
    }
    catch ( final Exception e )
    {
      onMessageProcessFailure( e );
      _schedulerActive = false;
      return false;
    }
    finally
    {
      if ( !_schedulerActive )
      {
        _schedulerLock.dispose();
        _schedulerLock = null;
      }
    }
    return _schedulerActive;
  }

  /**
   * Activate the scheduler.
   */
  protected abstract void doActivateScheduler();

  @SuppressWarnings( "SameParameterValue" )
  protected void setChangesToProcessPerTick( final int changesToProcessPerTick )
  {
    _changesToProcessPerTick = changesToProcessPerTick;
  }

  @SuppressWarnings( "SameParameterValue" )
  protected void setLinksToProcessPerTick( final int linksToProcessPerTick )
  {
    _linksToProcessPerTick = linksToProcessPerTick;
  }

  @Nonnull
  protected abstract ChangeSet parseChangeSet( @Nonnull String rawJsonData );

  /**
   * Perform a single step in sending one (or a batch) or requests to the server.
   *
   * @return true if more work is to be done.
   */
  protected boolean progressAreaOfInterestRequestProcessing()
  {
    final List<AreaOfInterestRequest> requests = ensureConnection().getCurrentAreaOfInterestRequests();
    if ( requests.isEmpty() )
    {
      return false;
    }
    else if ( requests.get( 0 ).isInProgress() )
    {
      return false;
    }
    else
    {
      requests.forEach( AreaOfInterestRequest::markAsInProgress );
      final AreaOfInterestAction action = requests.get( 0 ).getAction();
      if ( AreaOfInterestAction.ADD == action )
      {
        return progressAreaOfInterestAddRequests( requests );
      }
      else if ( AreaOfInterestAction.REMOVE == action )
      {
        return progressAreaOfInterestRemoveRequests( requests );
      }
      else
      {
        return progressAreaOfInterestUpdateRequests( requests );
      }
    }
  }

  @Nonnull
  private String label( @Nonnull final List<AreaOfInterestRequest> requests )
  {
    if ( requests.isEmpty() )
    {
      return "";
    }
    else
    {
      final Object filter = requests.get( 0 ).getFilter();
      return getKey() +
             ":" +
             requests.stream().map( e -> e.getAddress().toString() ).collect( Collectors.joining( "/" ) ) +
             ( null == filter ? "" : "[" + filterToString( filter ) + "]" );
    }
  }

  private boolean progressAreaOfInterestUpdateRequests( @Nonnull final List<AreaOfInterestRequest> requests )
  {
    removeUnneededUpdateRequests( requests );

    if ( requests.isEmpty() )
    {
      completeAreaOfInterestRequest();
      return true;
    }

    final Consumer<SafeProcedure> completionAction = a ->
    {
      LOG.warning( () -> "Subscription update of " + label( requests ) + " completed." );
      completeAreaOfInterestRequest();
      a.call();
    };
    final Consumer<SafeProcedure> failAction = a ->
    {
      LOG.warning( () -> "Subscription update of " + label( requests ) + " failed." );
      completeAreaOfInterestRequest();
      a.call();
    };
    LOG.warning( () -> "Subscription update of " + label( requests ) + " requested." );

    final AreaOfInterestRequest request = requests.get( 0 );
    assert null != request.getFilter();
    if ( requests.size() > 1 )
    {
      final List<ChannelAddress> addresses =
        requests.stream().map( AreaOfInterestRequest::getAddress ).collect( Collectors.toList() );
      requestBulkUpdateSubscription( addresses, request.getFilter(), completionAction, failAction );
    }
    else
    {
      requestUpdateSubscription( request.getAddress(), request.getFilter(), completionAction, failAction );
    }
    return true;
  }

  @Action
  protected void removeUnneededUpdateRequests( @Nonnull final List<AreaOfInterestRequest> requests )
  {
    requests.removeIf( a -> {
      final Subscription subscription = getReplicantContext().findSubscription( a.getAddress() );
      if ( null == subscription )
      {
        LOG.warning( () -> "Subscription update of " + a + " requested but not subscribed." );
        a.markAsComplete();
        return true;
      }
      return false;
    } );
  }

  private boolean progressAreaOfInterestRemoveRequests( @Nonnull final List<AreaOfInterestRequest> requests )
  {
    removeUnneededRemoveRequests( requests );

    if ( requests.isEmpty() )
    {
      completeAreaOfInterestRequest();
      return true;
    }

    LOG.info( () -> "Unsubscribe from " + label( requests ) + " requested." );
    final Consumer<SafeProcedure> completionAction = postAction ->
    {
      LOG.info( () -> "Unsubscribe from " + label( requests ) + " completed." );
      context().safeAction( generateName( "setExplicitSubscription(false)" ), () -> requests.forEach( a -> {
        final Subscription subscription = getReplicantContext().findSubscription( a.getAddress() );
        if ( null != subscription )
        {
          subscription.setExplicitSubscription( false );
        }
      } ) );
      completeAreaOfInterestRequest();
      postAction.call();
    };

    final Consumer<SafeProcedure> failAction = postAction ->
    {
      LOG.info( "Unsubscribe from " + label( requests ) + " failed." );
      context().safeAction( generateName( "setExplicitSubscription(false)" ), () -> requests.forEach( a -> {
        final Subscription subscription = getReplicantContext().findSubscription( a.getAddress() );
        if ( null != subscription )
        {
          subscription.setExplicitSubscription( false );
        }
      } ) );
      completeAreaOfInterestRequest();
      postAction.call();
    };

    final AreaOfInterestRequest request = requests.get( 0 );
    if ( requests.size() > 1 )
    {
      final List<ChannelAddress> addresses =
        requests.stream().map( AreaOfInterestRequest::getAddress ).collect( Collectors.toList() );
      requestBulkUnsubscribeFromChannel( addresses, completionAction, failAction );
    }
    else
    {
      requestUnsubscribeFromChannel( request.getAddress(), completionAction, failAction );
    }
    return true;
  }

  @Action
  protected void removeUnneededRemoveRequests( @Nonnull final List<AreaOfInterestRequest> requests )
  {
    requests.removeIf( a -> {
      final Subscription subscription = getReplicantContext().findSubscription( a.getAddress() );
      if ( null == subscription )
      {
        LOG.warning( () -> "Unsubscribe from " + a + " requested but not subscribed." );
        a.markAsComplete();
        return true;
      }
      else if ( !subscription.isExplicitSubscription() )
      {
        LOG.warning( () -> "Unsubscribe from " + a + " requested but not explicitly subscribed." );
        a.markAsComplete();
        return true;
      }
      return false;
    } );
  }

  private boolean progressAreaOfInterestAddRequests( @Nonnull final List<AreaOfInterestRequest> requests )
  {
    removeUnneededAddRequests( requests );

    if ( requests.isEmpty() )
    {
      completeAreaOfInterestRequest();
      return true;
    }

    final Consumer<SafeProcedure> completionAction = a ->
    {
      LOG.info( () -> "Subscription to " + label( requests ) + " completed." );
      completeAreaOfInterestRequest();
      a.call();
    };
    final Consumer<SafeProcedure> failAction = a ->
    {
      LOG.info( () -> "Subscription to " + label( requests ) + " failed." );
      completeAreaOfInterestRequest();
      a.call();
    };

    final AreaOfInterestRequest request = requests.get( 0 );
    if ( 1 == requests.size() )
    {
      final String cacheKey = request.getCacheKey();
      final CacheService cacheService = getReplicantContext().getCacheService();
      final CacheEntry cacheEntry = null == cacheService ? null : cacheService.lookup( cacheKey );
      final String eTag;
      final Consumer<SafeProcedure> cacheAction;
      if ( null != cacheEntry )
      {
        eTag = cacheEntry.getETag();
        LOG.info( () -> "Found locally cached data for channel " + request + " with etag " + eTag + "." );
        cacheAction = a ->
        {
          LOG.info( () -> "Loading cached data for channel " + request + " with etag " + eTag );
          final SafeProcedure completeCachedAction = () ->
          {
            LOG.info( () -> "Completed load of cached data for channel " + request + " with etag " + eTag + "." );
            completeAreaOfInterestRequest();
            a.call();
          };
          ensureConnection().enqueueOutOfBandResponse( cacheEntry.getContent(), completeCachedAction );
          triggerScheduler();
        };
      }
      else
      {
        eTag = null;
        cacheAction = null;
      }
      LOG.info( () -> "Subscription to " + request + " with eTag " + cacheKey + "=" + eTag + " requested" );
      requestSubscribeToChannel( request.getAddress(),
                                 request.getFilter(),
                                 cacheKey,
                                 eTag,
                                 cacheAction,
                                 completionAction,
                                 failAction );
    }
    else
    {
      // don't support bulk loading of anything that is already cached
      final List<ChannelAddress> ids =
        requests.stream().map( AreaOfInterestRequest::getAddress ).collect( Collectors.toList() );
      requestBulkSubscribeToChannel( ids, request.getFilter(), completionAction, failAction );
    }
    return true;
  }

  @Action
  protected void removeUnneededAddRequests( @Nonnull final List<AreaOfInterestRequest> requests )
  {
    requests.removeIf( a -> {
      final Subscription subscription = getReplicantContext().findSubscription( a.getAddress() );
      if ( null != subscription )
      {
        if ( subscription.isExplicitSubscription() )
        {
          LOG.warning( "Subscription to " + a + " requested but already subscribed." );
        }
        else
        {
          LOG.warning( () -> "Existing subscription to " + a + " converted to a explicit subscription." );
          subscription.setExplicitSubscription( true );
        }
        a.markAsComplete();
        return true;
      }
      return false;
    } );
  }

  private void completeAreaOfInterestRequest()
  {
    triggerScheduler();
    ensureConnection().completeAreaOfInterestRequest();
  }

  protected abstract void requestSubscribeToChannel( @Nonnull ChannelAddress address,
                                                     @Nullable Object filter,
                                                     @Nullable String cacheKey,
                                                     @Nullable String eTag,
                                                     @Nullable Consumer<SafeProcedure> cacheAction,
                                                     @Nonnull Consumer<SafeProcedure> completionAction,
                                                     @Nonnull Consumer<SafeProcedure> failAction );

  protected abstract void requestUnsubscribeFromChannel( @Nonnull ChannelAddress address,
                                                         @Nonnull Consumer<SafeProcedure> completionAction,
                                                         @Nonnull Consumer<SafeProcedure> failAction );

  protected abstract void requestUpdateSubscription( @Nonnull ChannelAddress address,
                                                     @Nonnull Object filter,
                                                     @Nonnull Consumer<SafeProcedure> completionAction,
                                                     @Nonnull Consumer<SafeProcedure> failAction );

  protected abstract void requestBulkSubscribeToChannel( @Nonnull List<ChannelAddress> addresses,
                                                         @Nullable Object filter,
                                                         @Nonnull Consumer<SafeProcedure> completionAction,
                                                         @Nonnull Consumer<SafeProcedure> failAction );

  protected abstract void requestBulkUnsubscribeFromChannel( @Nonnull List<ChannelAddress> addresses,
                                                             @Nonnull Consumer<SafeProcedure> completionAction,
                                                             @Nonnull Consumer<SafeProcedure> failAction );

  protected abstract void requestBulkUpdateSubscription( @Nonnull List<ChannelAddress> addresses,
                                                         @Nonnull Object filter,
                                                         @Nonnull Consumer<SafeProcedure> completionAction,
                                                         @Nonnull Consumer<SafeProcedure> failAction );

  protected boolean progressResponseProcessing()
  {
    final Connection connection = ensureConnection();
    // Step: Retrieve any out of band actions
    final LinkedList<MessageResponse> oobResponses = connection.getOutOfBandResponses();
    final MessageResponse response = connection.getCurrentMessageResponse();
    if ( null == response && !oobResponses.isEmpty() )
    {
      connection.setCurrentMessageResponse( oobResponses.removeFirst() );
      return true;
    }

    //Step: Retrieve the action from the parsed queue if it is the next in the sequence
    final LinkedList<MessageResponse> pendingResponses = connection.getPendingResponses();
    if ( null == response && !pendingResponses.isEmpty() )
    {
      final MessageResponse action = pendingResponses.get( 0 );
      final ChangeSet changeSet = action.getChangeSet();
      if ( action.isOob() || connection.getLastRxSequence() + 1 == changeSet.getSequence() )
      {
        final MessageResponse candidate = pendingResponses.remove();
        connection.setCurrentMessageResponse( candidate );
        return true;
      }
    }

    // Abort if there is no pending data load actions to take
    final LinkedList<MessageResponse> unparsedResponses = connection.getUnparsedResponses();
    if ( null == response && unparsedResponses.isEmpty() )
    {
      return false;
    }

    //Step: Retrieve the action from the un-parsed queue
    if ( null == response )
    {
      final MessageResponse candidate = unparsedResponses.remove();
      connection.setCurrentMessageResponse( candidate );
      return true;
    }

    //Step: Parse the json
    final String rawJsonData = response.getRawJsonData();
    boolean isOutOfBandMessage = response.isOob();
    if ( null != rawJsonData )
    {
      final ChangeSet changeSet = parseChangeSet( rawJsonData );
      if ( Replicant.shouldValidateChangeSetOnRead() )
      {
        changeSet.validate();
      }

      // OOB messages are not in response to requests as such
      final String requestID = isOutOfBandMessage ? null : changeSet.getRequestId();
      // OOB messages have no etags as from local cache or generated locally
      final String eTag = isOutOfBandMessage ? null : changeSet.getETag();
      final int sequence = isOutOfBandMessage ? 0 : changeSet.getSequence();
      final RequestEntry request;
      if ( isOutOfBandMessage )
      {
        request = null;
      }
      else
      {
        request = null != requestID ? connection.getRequest( requestID ) : null;
        if ( null == request && null != requestID )
        {
          final String message =
            "Unable to locate requestID '" + requestID + "' specified for ChangeSet: seq=" + sequence +
            " Existing Requests: " + connection.getRequests();
          if ( LOG.isLoggable( Level.WARNING ) )
          {
            LOG.warning( message );
          }
          throw new IllegalStateException( message );
        }
        else if ( null != request )
        {
          final String cacheKey = request.getCacheKey();
          if ( null != eTag && null != cacheKey )
          {
            if ( LOG.isLoggable( getLogLevel() ) )
            {
              LOG.log( getLogLevel(), "Caching ChangeSet: seq=" + sequence + " cacheKey=" + cacheKey );
            }
            final CacheService cacheService = getReplicantContext().getCacheService();
            if ( null != cacheService )
            {
              cacheService.store( cacheKey, eTag, rawJsonData );
            }
          }
        }
      }

      response.recordChangeSet( changeSet, request );
      pendingResponses.add( response );
      Collections.sort( pendingResponses );
      connection.setCurrentMessageResponse( null );
      return true;
    }

    if ( response.needsChannelActionsProcessed() )
    {
      processChannelChanges( response );
      return true;
    }

    //Step: Process a chunk of changes
    if ( response.areChangesPending() )
    {
      processEntityChanges( response );
      return true;
    }

    //Step: Process a chunk of links
    if ( response.areEntityLinksPending() )
    {
      processEntityLinks( response );
      return true;
    }

    //Step: Finalize the change set
    if ( !response.hasWorldBeenNotified() )
    {
      response.markWorldAsNotified();
      // OOB messages are not sequenced
      if ( !isOutOfBandMessage )
      {
        connection.setLastRxSequence( response.getChangeSet().getSequence() );
      }
      if ( Replicant.shouldValidateRepositoryOnLoad() )
      {
        validateRepository();
      }

      return true;
    }
    final DataLoadStatus status = response.toStatus();
    if ( LOG.isLoggable( Level.INFO ) )
    {
      LOG.info( status.toString() );
    }

    //Step: Run the post actions
    final RequestEntry request = response.getRequest();
    if ( null != request )
    {
      request.markResultsAsArrived();
    }
    final SafeProcedure runnable = response.getCompletionAction();
    if ( null != runnable )
    {
      runnable.call();
      // OOB messages are not in response to requests as such
      final String requestId = isOutOfBandMessage ? null : response.getChangeSet().getRequestId();
      if ( null != requestId )
      {
        // We can remove the request because this side ran second and the
        // RPC channel has already returned.

        final boolean removed = connection.removeRequest( requestId );
        if ( !removed )
        {
          LOG.severe( "ChangeSet " + response.getChangeSet().getSequence() + " expected to " +
                      "complete request '" + requestId + "' but no request was registered with connection." );
        }
        if ( requestDebugOutputEnabled() )
        {
          outputRequestDebug();
        }
      }
    }
    connection.setCurrentMessageResponse( null );
    onMessageProcessed( status );
    if ( null != _resetAction )
    {
      _resetAction.run();
      _resetAction = null;
    }
    return true;
  }

  @Action( reportParameters = false )
  protected void processEntityChanges( @Nonnull final MessageResponse currentAction )
  {
    EntityChange change;
    for ( int i = 0; i < _changesToProcessPerTick && null != ( change = currentAction.nextChange() ); i++ )
    {
      final Object entity = getChangeMapper().applyChange( change );
      if ( change.isUpdate() )
      {
        currentAction.incEntityUpdateCount();
        currentAction.changeProcessed( entity );
      }
      else
      {
        currentAction.incEntityRemoveCount();
      }
    }
  }

  @Action( reportParameters = false )
  protected void processEntityLinks( @Nonnull final MessageResponse currentAction )
  {
    Linkable linkable;
    for ( int i = 0; i < _linksToProcessPerTick && null != ( linkable = currentAction.nextEntityToLink() ); i++ )
    {
      linkable.link();
      currentAction.incEntityLinkCount();
    }
  }

  @Action
  protected void processChannelChanges( @Nonnull final MessageResponse currentAction )
  {
    currentAction.markChannelActionsProcessed();
    final ChangeSet changeSet = currentAction.getChangeSet();
    final ChannelChange[] channelChanges = changeSet.getChannelChanges();
    for ( final ChannelChange channelChange : channelChanges )
    {
      final ChannelAddress address = toAddress( channelChange );
      final Object filter = channelChange.getChannelFilter();
      final ChannelChange.Action actionType = channelChange.getAction();

      if ( ChannelChange.Action.ADD == actionType )
      {
        currentAction.incChannelAddCount();
        final boolean explicitSubscribe =
          ensureConnection()
            .getCurrentAreaOfInterestRequests()
            .stream()
            .anyMatch( a -> a.isInProgress() && a.getAddress().equals( address ) );
        getReplicantContext().createSubscription( address, filter, explicitSubscribe );
      }
      else if ( ChannelChange.Action.REMOVE == actionType )
      {
        final Subscription subscription = getReplicantContext().findSubscription( address );
        assert null != subscription;
        Disposable.dispose( subscription );
        currentAction.incChannelRemoveCount();
      }
      else if ( ChannelChange.Action.UPDATE == actionType )
      {
        final Subscription subscription = getReplicantContext().findSubscription( address );
        assert null != subscription;
        subscription.setFilter( filter );
        updateSubscriptionForFilteredEntities( subscription, filter );
        currentAction.incChannelUpdateCount();
      }
      else
      {
        throw new IllegalStateException();
      }
    }
  }

  protected abstract boolean requestDebugOutputEnabled();

  @Nonnull
  private ChannelAddress toAddress( @Nonnull final ChannelChange channelChange )
  {
    final int channelId = channelChange.getChannelId();
    final Integer subChannelId = channelChange.hasSubChannelId() ? channelChange.getSubChannelId() : null;
    final Enum channelType = channelIdToType( channelId );
    return new ChannelAddress( channelType, subChannelId );
  }

  protected abstract void updateSubscriptionForFilteredEntities( @Nonnull Subscription subscription,
                                                                 @Nullable Object filter );

  protected void updateSubscriptionForFilteredEntities( @Nonnull final Subscription subscription,
                                                        @Nullable final Object filter,
                                                        @Nonnull final Collection<Entity> entities )
  {
    if ( !entities.isEmpty() )
    {
      final ChannelAddress address = subscription.getAddress();
      for ( final Entity entity : new ArrayList<>( entities ) )
      {
        if ( !doesEntityMatchFilter( address, filter, entity ) )
        {
          entity.delinkFromSubscription( subscription );
        }
      }
    }
  }

  protected abstract boolean doesEntityMatchFilter( @Nonnull ChannelAddress address,
                                                    @Nullable Object filter,
                                                    @Nonnull Entity entity );

  /**
   * Return the type for specified channel.
   *
   * @param channel the channel code.
   * @return the channelType associated with channelId.
   * @throws IllegalArgumentException if no such channel
   */
  @Nonnull
  protected Enum channelIdToType( final int channel )
    throws IllegalArgumentException
  {
    assert getSystemType().isEnum();
    return (Enum) getSystemType().getEnumConstants()[ channel ];
  }

  @Nonnull
  protected String filterToString( @Nullable final Object filter )
  {
    if ( null == filter )
    {
      return "";
    }
    else
    {
      return doFilterToString( filter );
    }
  }

  @Nonnull
  protected abstract String doFilterToString( @Nonnull Object filter );

  @Nonnull
  protected Level getLogLevel()
  {
    return Level.FINEST;
  }

  /**
   * Check all the entities in the repository and raise an exception if an entity fails to validateRepository.
   * This method should not need a transaction ... unless an entity is invalid and there is unlinked data so we
   * wrap in an @Action just in case.
   *
   * An entity can fail to validateRepository if it is {@link Disposable} and {@link Disposable#isDisposed()} returns
   * true. An entity can also fail to validateRepository if it is {@link Verifiable} and {@link Verifiable#verify()}
   * throws an exception.
   */
  @Action
  protected void validateRepository()
  {
    if ( Replicant.shouldCheckInvariants() )
    {
      for ( final Class<?> entityType : getReplicantContext().findAllEntityTypes() )
      {
        for ( final Object entity : getReplicantContext().findAllEntitiesByType( entityType ) )
        {
          invariant( () -> !Disposable.isDisposed( entity ),
                     () -> "Invalid disposed entity found during validation. Entity: " + entity );
          try
          {
            Verifiable.verify( entity );
          }
          catch ( final Exception e )
          {
            final String message = "Entity failed to verify. Entity = " + entity;
            LOG.log( Level.WARNING, message, e );
            throw new IllegalStateException( message, e );
          }
        }
      }
    }
  }

  protected void outputRequestDebug()
  {
    getRequestDebugger().outputRequests( getKey() + ":", ensureConnection() );
  }

  @Nonnull
  protected RequestDebugger getRequestDebugger()
  {
    return new RequestDebugger();
  }

  @Nullable
  protected String generateName( @Nonnull final String name )
  {
    return Replicant.areNamesEnabled() ? toString() + "." + name : null;
  }

  @Override
  public String toString()
  {
    return Replicant.areNamesEnabled() ? "DataLoader[" + getKey() + "]" : super.toString();
  }
}
