package org.realityforge.replicant.client.runtime;

import java.util.logging.Logger;
import javax.annotation.Nonnull;

public class DebugAreaOfInterestListener
  implements AreaOfInterestListener
{
  private static final Logger LOG = Logger.getLogger( DebugAreaOfInterestListener.class.getName() );

  private final AreaOfInterestService _service;

  public DebugAreaOfInterestListener( final AreaOfInterestService service )
  {
    _service = service;
  }

  @Override
  public void subscriptionCreated( @Nonnull final Subscription subscription )
  {
    LOG.warning( "subscriptionCreated: " + subscription );
    emitAreaOfInterest();
  }

  @Override
  public void subscriptionUpdated( @Nonnull final Subscription subscription )
  {
    LOG.warning( "subscriptionUpdated: " + subscription );
    emitAreaOfInterest();
  }

  @Override
  public void subscriptionDeleted( @Nonnull final Subscription subscription )
  {
    LOG.warning( "subscriptionDeleted: " + subscription );
    emitAreaOfInterest();
  }

  private void emitAreaOfInterest()
  {
    LOG.info( "\n\nAreaOfInterest Database\nSubscriptions: " );
    for ( final Subscription s : _service.getSubscriptionsMap().values() )
    {
      LOG.info( "Subscription: " + s );
    }
    LOG.info( "\n" );
  }
}
