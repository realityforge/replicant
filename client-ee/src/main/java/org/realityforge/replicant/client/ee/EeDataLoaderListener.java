package org.realityforge.replicant.client.ee;

import java.lang.annotation.Annotation;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.enterprise.inject.spi.BeanManager;
import org.realityforge.replicant.client.transport.DataLoadStatus;
import org.realityforge.replicant.client.transport.DataLoaderListenerAdapter;
import org.realityforge.replicant.client.transport.DataLoaderService;

public class EeDataLoaderListener
  extends DataLoaderListenerAdapter
{
  private final BeanManager _beanManager;
  private final Annotation _eventQualifier;
  private final String _key;

  public EeDataLoaderListener( @Nonnull final BeanManager beanManager,
                               @Nonnull final Annotation eventQualifier,
                               @Nonnull final String key )
  {
    _beanManager = Objects.requireNonNull( beanManager );
    _eventQualifier = Objects.requireNonNull( eventQualifier );
    _key = Objects.requireNonNull( key );
  }

  @Override
  public void onDataLoadComplete( @Nonnull final DataLoaderService service, @Nonnull final DataLoadStatus status )
  {
    fireEvent( new DataLoadCompleteEvent( status ) );
  }

  @Override
  public void onConnect( @Nonnull final DataLoaderService service )
  {
    fireEvent( new ConnectEvent( _key ) );
  }

  @Override
  public void onInvalidConnect( @Nonnull final DataLoaderService service, @Nonnull final Throwable throwable )
  {
    fireEvent( new InvalidConnectEvent( _key, throwable ) );
  }

  @Override
  public void onDisconnect( @Nonnull final DataLoaderService service )
  {
    fireEvent( new DisconnectEvent( _key ) );
  }

  @Override
  public void onInvalidDisconnect( @Nonnull final DataLoaderService service, @Nonnull final Throwable throwable )
  {
    fireEvent( new InvalidDisconnectEvent( _key, throwable ) );
  }

  @Override
  public void onPollFailure( @Nonnull final DataLoaderService service, @Nonnull final Throwable throwable )
  {
    fireEvent( new PollErrorEvent( _key, throwable ) );
  }

  @Override
  public void onDataLoadFailure( @Nonnull final DataLoaderService service, @Nonnull final Throwable throwable )
  {
    fireEvent( new DataLoadFailureEvent( _key, throwable ) );
  }

  protected void fireEvent( @Nonnull final Object event )
  {
    _beanManager.fireEvent( event, _eventQualifier );
  }
}