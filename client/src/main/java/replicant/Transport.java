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
  void requestSubscribe( @Nonnull ChannelAddress address,
                         @Nullable Object filter,
                         @Nullable String cacheKey,
                         @Nullable String eTag,
                         @Nullable SafeProcedure onCacheValid,
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
