package replicant;

import arez.Disposable;
import arez.annotations.Action;
import arez.annotations.ArezComponent;
import arez.annotations.Feature;
import arez.annotations.Memoize;
import arez.annotations.Observable;
import arez.annotations.PreDispose;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import replicant.react4j.ReplicantSubscription;
import replicant.spy.AreaOfInterestDisposedEvent;
import replicant.spy.AreaOfInterestStatusUpdatedEvent;
import static org.realityforge.braincheck.Guards.*;

/**
 * The channel description declares a desired channel subscription and also
 * includes data on the current status of the subscription.
 */
@ArezComponent( observable = Feature.ENABLE, requireId = Feature.ENABLE )
public abstract class AreaOfInterest
  extends ReplicantService
{
  public enum Status
  {
    NOT_ASKED,
    LOADING,
    LOADED,
    LOAD_FAILED,
    UPDATING,
    UPDATED,
    UPDATE_FAILED,
    UNLOADING,
    UNLOADED,
    DELETED
  }

  @Nonnull
  private final ChannelAddress _address;
  @Nullable
  private Object _filter;
  @Nonnull
  private Status _status = Status.NOT_ASKED;
  /**
   * The {@link ReplicantSubscription} class uses reference counting to determine whether an AreaOfInterest
   * is still of interest. The assumption is that after the refCount reaches 0 then it is likely that there
   * is no longer any interest and it can be disposed.
   */
  private int _refCount;

  @Nonnull
  static AreaOfInterest create( @Nullable final ReplicantContext context,
                                @Nonnull final ChannelAddress address,
                                @Nullable final Object filter )
  {
    return new Arez_AreaOfInterest( context, address, filter );
  }

  AreaOfInterest( @Nullable final ReplicantContext context,
                  @Nonnull final ChannelAddress address,
                  @Nullable final Object filter )
  {
    super( context );
    _address = Objects.requireNonNull( address );
    _filter = filter;
  }

  public void incRefCount()
  {
    _refCount++;
  }

  public int getRefCount()
  {
    return _refCount;
  }

  public void decRefCount()
  {
    assert _refCount > 0;
    _refCount--;
    if ( 0 >= _refCount )
    {
      TemporalScheduler.schedule( this::tryDispose, 5 );
    }
  }

  private synchronized void tryDispose()
  {
    if ( Disposable.isNotDisposed( this ) && 0 >= getRefCount() )
    {
      Disposable.dispose( this );
    }
  }

  @PreDispose
  void preDispose()
  {
    if ( Replicant.areSpiesEnabled() && getReplicantContext().getSpy().willPropagateSpyEvents() )
    {
      getReplicantContext().getSpy().reportSpyEvent( new AreaOfInterestDisposedEvent( this ) );
    }
  }

  @Nonnull
  public ChannelAddress getAddress()
  {
    return _address;
  }

  @Observable
  @Nullable
  public Object getFilter()
  {
    return _filter;
  }

  void setFilter( @Nullable final Object filter )
  {
    _filter = filter;
  }

  @Observable( readOutsideTransaction = Feature.ENABLE )
  @Nonnull
  public Status getStatus()
  {
    return _status;
  }

  void setStatus( @Nonnull final Status status )
  {
    _status = Objects.requireNonNull( status );
  }

  @Observable
  @Nullable
  public abstract Throwable getError();

  abstract void setError( @Nullable Throwable error );

  @Memoize
  @Nullable
  public Subscription getSubscription()
  {
    final boolean expectSubscription = shouldExpectSubscription( getStatus() );
    return expectSubscription ? getReplicantContext().findSubscription( getAddress() ) : null;
  }

  /**
   * Update the status of the AreaOfInterest.
   */
  @Action
  void updateAreaOfInterest( @Nonnull final Status status, @Nullable final Throwable error )
  {
    if ( Replicant.shouldCheckApiInvariants() )
    {
      final boolean expectError = Status.LOAD_FAILED == status || Status.UPDATE_FAILED == status;

      final ChannelAddress address = getAddress();
      apiInvariant( () -> !expectError || null != error,
                    () -> "Replicant-0016: Invoked updateAreaOfInterest for channel at address " +
                          address + " with status " + status + " but failed to supply " +
                          "the expected error." );
      apiInvariant( () -> expectError || null == error,
                    () -> "Replicant-0017: Invoked updateAreaOfInterest for channel at address " +
                          address + " with status " + status + " and supplied an unexpected error." );
      // It is fine to get here where status == LOADING or NOT_ASKED but a subscription is already present.
      // as this is part of the process that notifies back-end of upgrade of implicit subscription to explicit
      // subscription
      apiInvariant( () -> !shouldExpectNoSubscription( status ) ||
                          null == getReplicantContext().findSubscription( getAddress() ),
                    () -> "Replicant-0019: Invoked updateAreaOfInterest for channel at address " +
                          address + " with status " + status + " and found unexpected subscription in the context." );
    }

    setStatus( status );
    setError( error );
    if ( Replicant.areSpiesEnabled() && getReplicantContext().getSpy().willPropagateSpyEvents() )
    {
      getReplicantContext().getSpy().reportSpyEvent( new AreaOfInterestStatusUpdatedEvent( this ) );
    }
  }

  /**
   * @see #shouldExpectNoSubscription(Status)
   */
  private boolean shouldExpectSubscription( @Nonnull final Status status )
  {
    return Status.LOADED == status ||
           Status.UPDATING == status ||
           Status.UPDATED == status ||
           Status.UPDATE_FAILED == status ||
           Status.UNLOADING == status;
  }

  /**
   * Return true when status indicates that there should defiantly not be a subscription present.
   * Note that {@link #shouldExpectSubscription(Status)} combined with this method does not cover all
   * statuses. In particular NOT_ASKED and LOADING can potentially have a subscription when we are working
   * through the process of notifying server of explicit subscription when there is a local implicit subscription.
   */
  private boolean shouldExpectNoSubscription( @Nonnull final Status status )
  {
    return Status.UNLOADED == status;
  }

  @Override
  public String toString()
  {
    if ( Replicant.areNamesEnabled() )
    {
      return "AreaOfInterest[" + _address +
             ( null == _filter ? "" : " Filter: " + FilterUtil.filterToString( _filter ) ) +
             " Status: " + _status + "]";
    }
    else
    {
      return super.toString();
    }
  }
}
