package org.realityforge.replicant.server.transport;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings( "WeakerAccess" )
public final class ChannelMetaData
{
  public enum FilterType
  {
    /**
     * No filtering
     */
    NONE,
    /**
     * Filtering occurs but no parameter is passed to control such behaviour. Filtering rules are internal to the data.
     */
    INTERNAL,
    /**
     * Filtering occurs and the client passes a filter parameter but can never change the filter parameter without unsubscribing and resubscribing to graph.
     */
    STATIC,
    /**
     * Filtering occurs and the client passes a filter parameter and can change the filter parameter.
     */
    DYNAMIC
  }

  private final int _channelId;
  @Nonnull
  private final String _name;
  private final boolean _typeGraph;
  @Nonnull
  private final FilterType _filterType;
  private final Class<?> _filterParameterType;
  private final boolean _cacheable;
  /**
   * Flag indicating whether the channel should able to be subscribed to externally.
   * i.e. Can this be explicitly subscribed.
   */
  private final boolean _external;

  public ChannelMetaData( final int channelId,
                          @Nonnull final String name,
                          final boolean isTypeGraph,
                          @Nonnull final FilterType filterType,
                          @Nullable final Class<?> filterParameterType,
                          final boolean cacheable,
                          final boolean external )
  {
    _channelId = channelId;
    _name = Objects.requireNonNull( name );
    _typeGraph = isTypeGraph;
    _filterType = Objects.requireNonNull( filterType );
    _filterParameterType = filterParameterType;
    if ( !hasFilterParameter() && null != filterParameterType )
    {
      throw new IllegalArgumentException( "FilterParameterType specified but filterType is set to " + filterType );
    }
    else if ( hasFilterParameter() && null == filterParameterType )
    {
      throw new IllegalArgumentException( "FilterParameterType not specified but filterType is set to " + filterType );
    }
    _cacheable = cacheable;
    _external = external;
  }

  public int getChannelId()
  {
    return _channelId;
  }

  @Nonnull
  public String getName()
  {
    return _name;
  }

  public boolean isTypeGraph()
  {
    return _typeGraph;
  }

  public boolean isInstanceGraph()
  {
    return !isTypeGraph();
  }

  @Nonnull
  public FilterType getFilterType()
  {
    return _filterType;
  }

  public boolean hasFilterParameter()
  {
    return FilterType.DYNAMIC == _filterType || FilterType.STATIC == _filterType;
  }

  @Nonnull
  public Class<?> getFilterParameterType()
  {
    if ( null == _filterParameterType )
    {
      throw new IllegalStateException( "getFilterParameterType invoked on unfiltered graph" );
    }
    return _filterParameterType;
  }

  public boolean isCacheable()
  {
    return _cacheable;
  }

  public boolean isExternal()
  {
    return _external;
  }
}
