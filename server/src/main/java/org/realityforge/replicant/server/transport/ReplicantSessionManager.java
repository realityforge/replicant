package org.realityforge.replicant.server.transport;

import java.io.Serializable;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.realityforge.replicant.server.ChangeSet;
import org.realityforge.replicant.server.ChannelDescriptor;

public interface ReplicantSessionManager
{
  /**
   * Status returned when attempting to subscribe.
   */
  enum CacheStatus
  {
    /**
     * The client supplied cacheKey is still valid and cached data should be reused.
     */
    USE,
    /**
     * The client did not supply cacheKey or it is out of date. Client cache should be refreshed from supplied data.
     */
    REFRESH,
    /**
     * The client did not supply cacheKey or it is out of date and the response is not cacheable. This may occur
     * if multiple subscriptions occur in a single subscribe call or attempting to subscribe to channels that are
     * already on the client.
     *
     * One day this may not be needed if the client can generate the cache from the in-memory representation rather
     * than the representation as it passes over the network.
     * TODO: Fix this.
     */
    IGNORE
  }

  /**
   * Return the key used to access session information.
   * Typically this is used as part of the cookie or header name in the dependent system.
   *
   * @return the key used to access session information.
   */
  @Nonnull
  String getSessionKey();

  /**
   * Return the session for specified ID.
   * Session ID's are effectively opaque.
   *
   * @param sessionID the session id.
   * @return the associated session or null if no such session.
   */
  @Nullable
  ReplicantSession getSession( @Nonnull String sessionID );

  /**
   * Return the st of valid session ids.
   *
   * @return the set of valid session ids.
   */
  @Nonnull
  Set<String> getSessionIDs();

  /**
   * Invalidate session with specified session ID.
   * Ignore if no session with specified id.
   *
   * @param sessionID the session id.
   * @return true if a session was invalidated, false otherwise.
   */
  boolean invalidateSession( @Nonnull String sessionID );

  /**
   * Create session for specified username.
   * It is assumed the username has already been authenticated and this is just tracking the session.
   *
   * @return the new session.
   */
  @Nonnull
  ReplicantSession createSession();

  /**
   * @return the metadata for replicant system.
   */
  @Nonnull
  SystemMetaData getSystemMetaData();

  @Nonnull
  CacheStatus subscribe( @Nonnull ReplicantSession session,
                         @Nonnull ChannelDescriptor descriptor,
                         boolean explicitlySubscribe,
                         @Nullable Object filter,
                         @Nonnull ChangeSet changeSet );

  void bulkSubscribe( @Nonnull ReplicantSession session,
                      int channelID,
                      @Nonnull Collection<Serializable> subChannelIDs,
                      @Nullable Object filter,
                      boolean explicitSubscribe,
                      @Nonnull ChangeSet changeSet );

  void delinkSubscription( @Nonnull ReplicantSession session,
                           @Nonnull ChannelDescriptor sourceGraph,
                           @Nonnull ChannelDescriptor targetGraph,
                           @Nonnull ChangeSet changeSet );

  void bulkDelinkSubscription( @Nonnull ReplicantSession session,
                               @Nonnull ChannelDescriptor sourceGraph,
                               int channelID,
                               @Nonnull Collection<Serializable> subChannelIDs,
                               @Nonnull ChangeSet changeSet );

  void updateSubscription( @Nonnull ReplicantSession session,
                           @Nonnull ChannelDescriptor descriptor,
                           @Nullable Object filter,
                           @Nonnull ChangeSet changeSet );

  void bulkUpdateSubscription( @Nonnull ReplicantSession session,
                               int channelID,
                               @Nonnull Collection<Serializable> subChannelIDs,
                               @Nullable Object filter,
                               @Nonnull ChangeSet changeSet );

  void unsubscribe( @Nonnull ReplicantSession session,
                    @Nonnull ChannelDescriptor descriptor,
                    boolean explicitUnsubscribe,
                    @Nonnull ChangeSet changeSet );

  void bulkUnsubscribe( @Nonnull ReplicantSession session,
                        int channelID,
                        @Nonnull Collection<Serializable> subChannelIDs,
                        boolean explicitUnsubscribe,
                        @Nonnull ChangeSet changeSet );
}
