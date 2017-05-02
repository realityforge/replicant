package org.realityforge.replicant.client.net.gwt;

import javax.annotation.Nonnull;
import org.realityforge.replicant.client.EntityRepository;
import org.realityforge.replicant.client.runtime.AreaOfInterestService;
import org.realityforge.replicant.client.runtime.BaseRuntimeExtension;
import org.realityforge.replicant.client.runtime.ContextConverger;
import org.realityforge.replicant.client.runtime.ReplicantClientSystem;

public abstract class AbstractFrontendContextImpl
  implements BaseFrontendContext, BaseRuntimeExtension
{
  private final EntityRepository _repository;
  private final ReplicantClientSystem _replicantClientSystem;
  private final AreaOfInterestService _areaOfInterestService;
  private final ContextConverger _converger;

  public AbstractFrontendContextImpl( @Nonnull final ContextConverger converger,
                                      @Nonnull final EntityRepository repository,
                                      @Nonnull final ReplicantClientSystem replicantClientSystem,
                                      @Nonnull final AreaOfInterestService areaOfInterestService )
  {
    _areaOfInterestService = areaOfInterestService;
    _repository = repository;
    _replicantClientSystem = replicantClientSystem;

    _converger = converger;
    _converger.pauseAndRun( this::initialSubscriptionSetup );
    _converger.setPreConvergeAction( this::preConverge );
  }

  protected void preConverge()
  {
  }

  @Override
  public void disconnect()
  {
    _converger.deactivate();
    _replicantClientSystem.deactivate();
  }

  @Override
  public void connect()
  {
    _replicantClientSystem.activate();
    _converger.activate();
  }

  @Nonnull
  @Override
  public AreaOfInterestService getAreaOfInterestService()
  {
    return _areaOfInterestService;
  }

  @Nonnull
  @Override
  public ContextConverger getContextConverger()
  {
    return _converger;
  }

  @Nonnull
  @Override
  public EntityRepository getRepository()
  {
    return _repository;
  }

  protected void initialSubscriptionSetup()
  {
  }
}
