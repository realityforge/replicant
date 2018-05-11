package org.realityforge.replicant.client.transport;

import java.util.HashSet;
import java.util.LinkedList;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.realityforge.replicant.client.Linkable;
import replicant.EntityChange;
import replicant.spy.DataLoadStatus;

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

  private LinkedList<Linkable> _updatedEntities = new LinkedList<>();
  private HashSet<Linkable> _removedEntities = new HashSet<>();
  private LinkedList<Linkable> _entitiesToLink;
  private boolean _entityLinksCalculated;
  private boolean _worldNotified;
  private boolean _channelActionsProcessed;
  private RequestEntry _request;

  private int _channelAddCount;
  private int _channelUpdateCount;
  private int _channelRemoveCount;
  private int _entityUpdateCount;
  private int _entityRemoveCount;
  private int _entityLinkCount;

  DataLoadAction( @Nonnull final String rawJsonData, final boolean oob )
  {
    _rawJsonData = rawJsonData;
    _oob = oob;
  }

  int getChannelAddCount()
  {
    return _channelAddCount;
  }

  int getChannelRemoveCount()
  {
    return _channelRemoveCount;
  }

  void incChannelAddCount()
  {
    _channelAddCount++;
  }

  void incChannelUpdateCount()
  {
    _channelUpdateCount++;
  }

  void incChannelRemoveCount()
  {
    _channelRemoveCount++;
  }

  void incUpdateCount()
  {
    _entityUpdateCount++;
  }

  void incRemoveCount()
  {
    _entityRemoveCount++;
  }

  void incLinkCount()
  {
    _entityLinkCount++;
  }

  boolean isOob()
  {
    return _oob;
  }

  @Nullable
  String getRawJsonData()
  {
    return _rawJsonData;
  }

  void setChangeSet( @Nullable final ChangeSet changeSet, @Nullable final RequestEntry request )
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

  boolean areChangesPending()
  {
    return null != _changeSet && _changeIndex < _changeSet.getEntityChanges().length;
  }

  boolean needsChannelActionsProcessed()
  {
    return null != _changeSet && 0 != _changeSet.getChannelChanges().length && !_channelActionsProcessed;
  }

  void markChannelActionsProcessed()
  {
    _channelActionsProcessed = true;
  }

  EntityChange nextChange()
  {
    if ( areChangesPending() )
    {
      assert null != _changeSet;
      final EntityChange change = _changeSet.getEntityChanges()[ _changeIndex ];
      _changeIndex++;
      return change;
    }
    else
    {
      return null;
    }
  }

  void changeProcessed( final boolean isUpdate, final Object entity )
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

  boolean areEntityLinksCalculated()
  {
    return _entityLinksCalculated;
  }

  void calculateEntitiesToLink()
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

  boolean areEntityLinksPending()
  {
    return null != _entitiesToLink && !_entitiesToLink.isEmpty();
  }

  Linkable nextEntityToLink()
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
  ChangeSet getChangeSet()
  {
    return _changeSet;
  }

  void setRunnable( @Nullable final Runnable runnable )
  {
    assert isOob();
    _runnable = runnable;
  }

  @Nullable
  Runnable getRunnable()
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

  void markWorldAsNotified()
  {
    _worldNotified = true;
  }

  boolean hasWorldBeenNotified()
  {
    return _worldNotified;
  }

  @Nonnull
  DataLoadStatus toStatus( @Nonnull final String systemKey )
  {
    final ChangeSet changeSet = getChangeSet();
    assert null != changeSet;
    return new DataLoadStatus( systemKey,
                               changeSet.getSequence(),
                               changeSet.getRequestID(),
                               _channelAddCount,
                               _channelUpdateCount,
                               _channelRemoveCount,
                               _entityUpdateCount,
                               _entityRemoveCount,
                               _entityLinkCount );
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
