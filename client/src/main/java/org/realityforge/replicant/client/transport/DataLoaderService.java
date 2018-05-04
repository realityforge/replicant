package org.realityforge.replicant.client.transport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import replicant.ChannelAddress;

/**
 * Basic interface for interacting with data loader services.
 */
public interface DataLoaderService
{
  enum State
  {
    /// The service is not yet connected or has been disconnected
    DISCONNECTED,
    /// The service has started connecting but connection has not completed.
    CONNECTING,
    /// The service is connected.
    CONNECTED,
    /// The service has started disconnecting but disconnection has not completed.
    DISCONNECTING,
    /// The service is in error state. This error may occur during connection, disconnection or in normal operation.
    ERROR
  }

  /**
   * Return the state of the service.
   */
  @Nonnull
  State getState();

  /**
   * Connect to the underlying data source.
   * When connection is complete then execute passed runnable.
   */
  void connect();

  /**
   * Disconnect from underlying data source.
   * When disconnection is complete then execute passed runnable. The runnable
   * will also be invoked if the data loader service is not currently connected.
   */
  void disconnect();

  /**
   * Schedule a data load.
   */
  void scheduleDataLoad();

  /**
   * Add a listener. Return true if actually added, false if listener was already present.
   */
  boolean addDataLoaderListener( @Nonnull DataLoaderListener listener );

  /**
   * Remove a listener. Return true if actually removed, false if listener was not present.
   */
  boolean removeDataLoaderListener( @Nonnull DataLoaderListener listener );

  /**
   * A symbolic key for describing system.
   */
  @Nonnull
  String getKey();

  /**
   * Return the class of channels that this loader processes.
   */
  @Nonnull
  Class<? extends Enum> getSystemType();

  /**
   * Return true if the DataLoader has nothing outstanding to complete
   */
  boolean isIdle();

  /**
   * Return true if an area of interest action with specified parameters is pending or being processed.
   * When the action parameter is DELETE the filter parameter is ignored.
   */
  boolean isAreaOfInterestActionPending( @Nonnull AreaOfInterestAction action,
                                         @Nonnull ChannelAddress address,
                                         @Nullable Object filter );

  /**
   * Return the index of last matching AreaOfInterestAction in pending aoi actions list.
   */
  int indexOfPendingAreaOfInterestAction( @Nonnull AreaOfInterestAction action,
                                          @Nonnull ChannelAddress address,
                                          @Nullable Object filter );

  void requestSubscribe( @Nonnull ChannelAddress address, @Nullable Object filterParameter );

  void requestSubscriptionUpdate( @Nonnull ChannelAddress address, @Nullable Object filterParameter );

  void requestUnsubscribe( @Nonnull ChannelAddress address );
}
