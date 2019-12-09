package org.realityforge.replicant.server.transport;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.websocket.CloseReason;
import javax.websocket.Session;
import org.realityforge.replicant.server.ChangeSet;
import org.realityforge.replicant.server.ChannelAddress;
import org.realityforge.replicant.server.json.JsonEncoder;

public final class ReplicantSession
  implements Serializable, Closeable
{
  @Nullable
  private String _authToken;
  @Nonnull
  private final Session _webSocketSession;
  @Nonnull
  private final HashMap<ChannelAddress, String> _eTags = new HashMap<>();
  @Nonnull
  private final HashMap<ChannelAddress, SubscriptionEntry> _subscriptions = new HashMap<>();

  public ReplicantSession( @Nonnull final Session webSocketSession )
  {
    _webSocketSession = Objects.requireNonNull( webSocketSession );
  }

  public void close( @Nonnull final CloseReason closeReason )
  {
    if ( _webSocketSession.isOpen() )
    {
      try
      {
        _webSocketSession.close( closeReason );
      }
      catch ( final IOException ignored )
      {
        // Assume it is already closing
      }
    }
  }

  @Override
  public void close()
  {
    if ( _webSocketSession.isOpen() )
    {
      try
      {
        _webSocketSession.close();
      }
      catch ( final IOException ignored )
      {
        // Assume it is already closing
      }
    }
  }

  @Nonnull
  public Session getWebSocketSession()
  {
    return _webSocketSession;
  }

  public void setAuthToken( @Nullable final String authToken )
  {
    _authToken = authToken;
  }

  /**
   * @return a token used for authentication, if any.
   */
  @Nullable
  public String getAuthToken()
  {
    return _authToken;
  }

  /**
   * @return an opaque ID representing session.
   */
  @Nonnull
  public String getId()
  {
    return getWebSocketSession().getId();
  }

  /**
   * Add packet to queue and potentially send packet to client if client has acked last message.
   *
   * @param requestId the opaque identifier indicating the request that caused the changes if the owning session initiated the changes.
   * @param etag      the opaque identifier identifying the version. May be null if packet is not cache-able
   * @param changeSet the changeSet to create packet from.
   */
  public void sendPacket( @Nullable final Integer requestId,
                          @Nullable final String etag,
                          @Nonnull final ChangeSet changeSet )
  {
    WebSocketUtil.sendText( getWebSocketSession(), JsonEncoder.encodeChangeSet( requestId, etag, changeSet ) );
  }

  @SuppressWarnings( "WeakerAccess" )
  @Nonnull
  public Map<ChannelAddress, String> getETags()
  {
    return Collections.unmodifiableMap( _eTags );
  }

  @SuppressWarnings( "WeakerAccess" )
  @Nullable
  public String getETag( @Nonnull final ChannelAddress address )
  {
    return _eTags.get( address );
  }

  @SuppressWarnings( "WeakerAccess" )
  public void setETag( @Nonnull final ChannelAddress address, @Nullable final String eTag )
  {
    if ( null == eTag )
    {
      _eTags.remove( address );
    }
    else
    {
      _eTags.put( address, eTag );
    }
  }

  @Nonnull
  public Map<ChannelAddress, SubscriptionEntry> getSubscriptions()
  {
    return Collections.unmodifiableMap( _subscriptions );
  }

  /**
   * Return subscription entry for specified channel.
   */
  @SuppressWarnings( "WeakerAccess" )
  @Nonnull
  public SubscriptionEntry getSubscriptionEntry( @Nonnull final ChannelAddress address )
  {
    final SubscriptionEntry entry = findSubscriptionEntry( address );
    if ( null == entry )
    {
      throw new IllegalStateException( "Unable to locate subscription entry for " + address );
    }
    return entry;
  }

  /**
   * Create and return a subscription entry for specified channel.
   *
   * @throws IllegalStateException if subscription already exists.
   */
  @Nonnull
  public SubscriptionEntry createSubscriptionEntry( @Nonnull final ChannelAddress address )
  {
    if ( !_subscriptions.containsKey( address ) )
    {
      final SubscriptionEntry entry = new SubscriptionEntry( address );
      _subscriptions.put( address, entry );
      return entry;
    }
    else
    {
      throw new IllegalStateException( "SubscriptionEntry for channel " + address + " already exists" );
    }
  }

  /**
   * Return subscription entry for specified channel.
   */
  @Nullable
  public SubscriptionEntry findSubscriptionEntry( @Nonnull final ChannelAddress address )
  {
    return _subscriptions.get( address );
  }

  /**
   * Return true if specified channel is present.
   */
  @SuppressWarnings( "WeakerAccess" )
  public boolean isSubscriptionEntryPresent( @Nonnull final ChannelAddress address )
  {
    return null != findSubscriptionEntry( address );
  }

  /**
   * Delete specified subscription entry.
   */
  boolean deleteSubscriptionEntry( @Nonnull final SubscriptionEntry entry )
  {
    return null != _subscriptions.remove( entry.getDescriptor() );
  }
}
