package org.realityforge.replicant.client.transport;

import arez.Arez;
import arez.annotations.ArezComponent;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import replicant.ChannelAddress;
import replicant.Entity;
import replicant.Replicant;
import replicant.ReplicantContext;
import replicant.SafeProcedure;
import replicant.Subscription;
import replicant.spy.DataLoadStatus;
import static org.mockito.Mockito.*;

@ArezComponent
abstract class TestDataLoadService
  extends AbstractDataLoaderService
{
  private final CacheService _cacheService;
  private boolean _scheduleDataLoadCalled;
  private LinkedList<TestChangeSet> _changeSets = new LinkedList<>();
  private int _terminateCount;
  private DataLoadStatus _status;
  private final SessionContext _sessionContext;
  private final ChangeMapper _changeMapper;
  private int _validateRepositoryCallCount;

  @Nonnull
  static TestDataLoadService create()
  {
    return create( Replicant.areZonesEnabled() ? Replicant.context() : null );
  }

  @Nonnull
  private static TestDataLoadService create( @Nullable final ReplicantContext context )
  {
    return Arez.context()
      .safeAction( () -> new Arez_TestDataLoadService( context, mock( CacheService.class ) ) );
  }

  TestDataLoadService( @Nullable final ReplicantContext context,
                       @Nonnull final CacheService cacheService )
  {
    super( context, TestSystem.class, cacheService );
    _sessionContext = new SessionContext( "X" );
    _changeMapper = mock( ChangeMapper.class );
    _cacheService = cacheService;
  }

  CacheService getCacheService()
  {
    return _cacheService;
  }

  @Nonnull
  @Override
  protected SessionContext getSessionContext()
  {
    return _sessionContext;
  }

  @Nonnull
  @Override
  protected ChangeMapper getChangeMapper()
  {
    return _changeMapper;
  }

  @Nonnull
  @Override
  public ClientSession ensureSession()
  {
    final ClientSession session = getSession();
    assert null != session;
    return session;
  }

  @Override
  protected void doConnect( @Nullable final SafeProcedure runnable )
  {
  }

  @Override
  protected void doDisconnect( @Nullable final SafeProcedure runnable )
  {
  }

  @Override
  protected void validateRepository()
    throws IllegalStateException
  {
    _validateRepositoryCallCount += 1;
  }

  int getValidateRepositoryCallCount()
  {
    return _validateRepositoryCallCount;
  }

  void setChangeSets( final TestChangeSet... changeSets )
  {
    _changeSets.addAll( Arrays.asList( changeSets ) );
  }

  LinkedList<TestChangeSet> getChangeSets()
  {
    return _changeSets;
  }

  @Override
  protected void onTerminatingIncrementalDataLoadProcess()
  {
    _terminateCount++;
  }

  int getTerminateCount()
  {
    return _terminateCount;
  }

  boolean isScheduleDataLoadCalled()
  {
    return _scheduleDataLoadCalled;
  }

  @Override
  protected void onMessageProcessed( @Nonnull final DataLoadStatus status )
  {
    super.onMessageProcessed( status );
    _status = status;
  }

  DataLoadStatus getStatus()
  {
    return _status;
  }

  boolean isDataLoadComplete()
  {
    return null != _status;
  }

  @Override
  protected void doScheduleDataLoad()
  {
    _scheduleDataLoadCalled = true;
  }

  @Override
  protected boolean requestDebugOutputEnabled()
  {
    return false;
  }

  @Nonnull
  @Override
  protected ChangeSet parseChangeSet( @Nonnull final String rawJsonData )
  {
    return _changeSets.pop();
  }

  @Override
  protected void requestSubscribeToChannel( @Nonnull final ChannelAddress descriptor,
                                            @Nullable final Object filterParameter,
                                            @Nullable final String cacheKey,
                                            @Nullable final String eTag,
                                            @Nullable final Consumer<Runnable> cacheAction,
                                            @Nonnull final Consumer<Runnable> completionAction,
                                            @Nonnull final Consumer<Runnable> failAction )
  {
  }

  @Override
  protected void requestUnsubscribeFromChannel( @Nonnull final ChannelAddress descriptor,
                                                @Nonnull final Consumer<Runnable> completionAction,
                                                @Nonnull final Consumer<Runnable> failAction )
  {
  }

  @Override
  protected void requestUpdateSubscription( @Nonnull final ChannelAddress descriptor,
                                            @Nonnull final Object filterParameter,
                                            @Nonnull final Consumer<Runnable> completionAction,
                                            @Nonnull final Consumer<Runnable> failAction )
  {
  }

  @Override
  protected void requestBulkSubscribeToChannel( @Nonnull final List<ChannelAddress> descriptor,
                                                @Nullable final Object filterParameter,
                                                @Nonnull final Consumer<Runnable> completionAction,
                                                @Nonnull final Consumer<Runnable> failAction )
  {
  }

  @Override
  protected void requestBulkUnsubscribeFromChannel( @Nonnull final List<ChannelAddress> descriptors,
                                                    @Nonnull final Consumer<Runnable> completionAction,
                                                    @Nonnull final Consumer<Runnable> failAction )
  {
  }

  @Override
  protected void requestBulkUpdateSubscription( @Nonnull final List<ChannelAddress> descriptors,
                                                @Nonnull final Object filterParameter,
                                                @Nonnull final Consumer<Runnable> completionAction,
                                                @Nonnull final Consumer<Runnable> failAction )
  {
  }

  @Override
  protected boolean doesEntityMatchFilter( @Nonnull final ChannelAddress address,
                                           @Nullable final Object filter,
                                           @Nonnull final Entity entity )
  {
    return entity.getId() < 0;
  }

  @Nonnull
  @Override
  protected String doFilterToString( @Nonnull final Object filterParameter )
  {
    return String.valueOf( filterParameter );
  }

  @Override
  protected void updateSubscriptionForFilteredEntities( @Nonnull final Subscription subscription,
                                                        @Nullable final Object filter )
  {
  }
}
