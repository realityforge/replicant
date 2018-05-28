package replicant;

import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The transport is responsible for communicating with the backend system.
 */
public interface Transport
{
  interface OnConnect
  {
    void onConnect( @Nonnull String connectionId );
  }

  interface OnError
  {
    void onError( @Nonnull Throwable error );
  }

  /**
   * Perform the connection, invoking the action when connection has completed.
   *
   * @param onConnect the action to invoke once connect has completed.
   * @param onConnectError the action to invoke if connect errors.
   */
  void connect( @Nonnull OnConnect onConnect, @Nonnull OnError onConnectError );

  /**
   * Perform the disconnection, invoking the action when disconnection has completed.
   *
   * @param action the action to invoke once disconnect has completed.
   */
  void disconnect( @Nonnull SafeProcedure action );

  void requestSubscribe( @Nonnull ChannelAddress address,
                         @Nullable Object filter,
                         @Nonnull String eTag,
                         @Nonnull SafeProcedure onCacheValid,
                         @Nonnull SafeProcedure onSuccess,
                         @Nonnull Consumer<Throwable> onError );

  void requestSubscribe( @Nonnull ChannelAddress address,
                         @Nullable Object filter,
                         @Nonnull SafeProcedure onSuccess,
                         @Nonnull Consumer<Throwable> onError );

  void requestUnsubscribe( @Nonnull ChannelAddress address,
                           @Nonnull SafeProcedure onSuccess,
                           @Nonnull Consumer<Throwable> onError );

  void requestSubscriptionUpdate( @Nonnull ChannelAddress address,
                                  @Nonnull Object filter,
                                  @Nonnull SafeProcedure onSuccess,
                                  @Nonnull Consumer<Throwable> onError );

  void requestBulkSubscribe( @Nonnull List<ChannelAddress> addresses,
                             @Nullable Object filter,
                             @Nonnull SafeProcedure onSuccess,
                             @Nonnull Consumer<Throwable> onError );

  void requestBulkUnsubscribe( @Nonnull List<ChannelAddress> addresses,
                               @Nonnull SafeProcedure onSuccess,
                               @Nonnull Consumer<Throwable> onError );

  void requestBulkSubscriptionUpdate( @Nonnull List<ChannelAddress> addresses,
                                      @Nonnull Object filter,
                                      @Nonnull SafeProcedure onSuccess,
                                      @Nonnull Consumer<Throwable> onError );
}
