package org.realityforge.replicant.client.transport;

import java.util.HashSet;
import java.util.LinkedList;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.realityforge.replicant.client.Change;
import org.realityforge.replicant.client.ChangeSet;
import org.realityforge.replicant.client.Linkable;

/**
 * A simple class encapsulating the process of loading data from a json change set.
 */
final class DataLoadAction
  implements Comparable<DataLoadAction>
{
  /**
   * The raw data string data prior to parsing. Null-ed after parsing.
   */
  @Nullable
  private String _rawJsonData;

  /**
   * Is the message out-of-band ?
   */
  private final boolean _oob;

  /**
   * The array of changes after parsing. Null prior to parsing.
   */
  @Nullable
  private ChangeSet _changeSet;

  /**
   * The runnable action that may have been explicitly set for oob message.
   */
  @Nullable
  private Runnable _runnable;

  /**
   * The current index into changes.
   */
  private int _changeIndex;

  private LinkedList<ChannelChangeStatus> _channelAdds = new LinkedList<>();
  private LinkedList<ChannelChangeStatus> _channelUpdates = new LinkedList<>();
  private LinkedList<ChannelChangeStatus> _channelRemoves = new LinkedList<>();
  private LinkedList<Linkable> _updatedEntities = new LinkedList<>();
  private HashSet<Linkable> _removedEntities = new HashSet<>();
  private LinkedList<Linkable> _entitiesToLink;
  private boolean _entityLinksCalculated;
  private boolean _worldNotified;
  private boolean _channelActionsProcessed;
  private boolean _brokerPaused;
  private RequestEntry _request;

  private int _updateCount;
  private int _removeCount;
  private int _linkCount;

  public DataLoadAction( @Nonnull final String rawJsonData, final boolean oob )
  {
    _rawJsonData = rawJsonData;
    _oob = oob;
  }

  public int getChannelAddCount()
  {
    return _channelAdds.size();
  }

  public int getChannelUpdateCount()
  {
    return _channelUpdates.size();
  }

  public int getChannelRemoveCount()
  {
    return _channelRemoves.size();
  }

  public LinkedList<ChannelChangeStatus> getChannelAdds()
  {
    return _channelAdds;
  }

  public LinkedList<ChannelChangeStatus> getChannelUpdates()
  {
    return _channelUpdates;
  }

  public LinkedList<ChannelChangeStatus> getChannelRemoves()
  {
    return _channelRemoves;
  }

  public int getUpdateCount()
  {
    return _updateCount;
  }

  public int getRemoveCount()
  {
    return _removeCount;
  }

  public int getLinkCount()
  {
    return _linkCount;
  }

  public void recordChannelSubscribe( final ChannelChangeStatus descriptor )
  {
    _channelAdds.add( descriptor );
  }

  public void recordChannelUnsubscribe( final ChannelChangeStatus descriptor )
  {
    _channelRemoves.add( descriptor );
  }

  public void recordChannelSubscriptionUpdate( final ChannelChangeStatus descriptor )
  {
    _channelUpdates.add( descriptor );
  }

  public void incUpdateCount()
  {
    _updateCount++;
  }

  public void incRemoveCount()
  {
    _removeCount++;
  }

  public void incLinkCount()
  {
    _linkCount++;
  }

  public boolean isOob()
  {
    return _oob;
  }

  @Nullable
  public String getRawJsonData()
  {
    return _rawJsonData;
  }

  public void setChangeSet( @Nullable final ChangeSet changeSet, @Nullable final RequestEntry request )
  {
    assert !isOob() || null == request;
    _request = request;
    _changeSet = changeSet;
    _rawJsonData = null;
    _changeIndex = 0;
  }

  public RequestEntry getRequest()
  {
    return _request;
  }

  public boolean areChangesPending()
  {
    return null != _changeSet && _changeIndex < _changeSet.getChangeCount();
  }

  final boolean needsChannelActionsProcessed()
  {
    return null != _changeSet && 0 != _changeSet.getChannelActionCount() && !_channelActionsProcessed;
  }

  public void markChannelActionsProcessed()
  {
    _channelActionsProcessed = true;
  }

  public boolean needsBrokerPause()
  {
    return !_brokerPaused &&
           ( null != _changeSet && ( 0 != _changeSet.getChangeCount() || 0 != _changeSet.getChannelActionCount() ) );
  }

  public boolean hasBrokerBeenPaused()
  {
    return _brokerPaused;
  }

  public void markBrokerPaused()
  {
    _brokerPaused = true;
  }

  public Change nextChange()
  {
    if ( areChangesPending() )
    {
      assert null != _changeSet;
      final Change change = _changeSet.getChange( _changeIndex );
      _changeIndex++;
      return change;
    }
    else
    {
      return null;
    }
  }

  public void changeProcessed( final boolean isUpdate, final Object entity )
  {
    if ( entity instanceof Linkable )
    {
      if ( isUpdate )
      {
        _updatedEntities.add( (Linkable) entity );
      }
      else
      {
        _removedEntities.add( (Linkable) entity );
      }
    }
  }

  public boolean areEntityLinksCalculated()
  {
    return _entityLinksCalculated;
  }

  public void calculateEntitiesToLink()
  {
    _entityLinksCalculated = true;
    _entitiesToLink = new LinkedList<>();
    for ( final Linkable entity : _updatedEntities )
    {
      // In some circumstances a create and remove can appear in same change set so guard against this
      if ( !_removedEntities.contains( entity ) )
      {
        _entitiesToLink.add( entity );
      }
    }
    _updatedEntities = null;
    _removedEntities = null;
  }

  public boolean areEntityLinksPending()
  {
    return null != _entitiesToLink && !_entitiesToLink.isEmpty();
  }

  public Linkable nextEntityToLink()
  {
    if ( areEntityLinksPending() )
    {
      assert null != _entitiesToLink;
      return _entitiesToLink.remove();
    }
    else
    {
      _entitiesToLink = null;
      return null;
    }
  }

  @Nullable
  public ChangeSet getChangeSet()
  {
    return _changeSet;
  }

  public void setRunnable( @Nullable final Runnable runnable )
  {
    assert isOob();
    _runnable = runnable;
  }

  @Nullable
  public Runnable getRunnable()
  {
    if ( null != _runnable )
    {
      return _runnable;
    }
    else if ( null == _request || !_request.isCompletionDataPresent() )
    {
      return null;
    }
    else
    {
      return _request.getCompletionAction();
    }
  }

  public void markWorldAsNotified()
  {
    _worldNotified = true;
  }

  public boolean hasWorldBeenNotified()
  {
    return _worldNotified;
  }

  @Nonnull
  public DataLoadStatus toStatus( @Nonnull final String systemKey )
  {
    final ChangeSet changeSet = getChangeSet();
    assert null != changeSet;
    return new DataLoadStatus( systemKey,
                               changeSet.getSequence(),
                               changeSet.getRequestID(),
                               getChannelAdds(),
                               _channelUpdates,
                               _channelRemoves,
                               getUpdateCount(),
                               getRemoveCount(),
                               getLinkCount() );
  }

  @Override
  public String toString()
  {
    return "DataLoad[" +
           ",RawJson.null?=" + ( _rawJsonData == null ) +
           ",ChangeSet.null?=" + ( _changeSet == null ) +
           ",ChangeIndex=" + _changeIndex +
           ",Runnable.null?=" + ( getRunnable() == null ) +
           ",UpdatedEntities.size=" + ( _updatedEntities != null ? _updatedEntities.size() : null ) +
           ",RemovedEntities.size=" + ( _removedEntities != null ? _removedEntities.size() : null ) +
           ",EntitiesToLink.size=" + ( _entitiesToLink != null ? _entitiesToLink.size() : null ) +
           ",EntityLinksCalculated=" + _entityLinksCalculated +
           "]";
  }

  @Override
  public int compareTo( @Nonnull final DataLoadAction other )
  {
    if ( isOob() && other.isOob() )
    {
      return 0;
    }
    else if ( isOob() )
    {
      return -1;
    }
    else if ( other.isOob() )
    {
      return 1;
    }
    else
    {
      final ChangeSet changeSet1 = getChangeSet();
      assert null != changeSet1;
      final ChangeSet changeSet2 = other.getChangeSet();
      assert null != changeSet2;
      return changeSet1.getSequence() - changeSet2.getSequence();
    }
  }
}
