package replicant;

import arez.Arez;
import arez.ArezContext;
import arez.Disposable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.realityforge.guiceyloops.shared.ValueUtil;
import org.testng.annotations.Test;
import replicant.spy.SubscribeRequestQueuedEvent;
import replicant.spy.SubscriptionOrphanedEvent;
import replicant.spy.SubscriptionUpdateRequestQueuedEvent;
import replicant.spy.UnsubscribeRequestQueuedEvent;
import static org.testng.Assert.*;

@SuppressWarnings( "Duplicates" )
public class ConvergerTest
  extends AbstractReplicantTest
{
  @Test
  public void construct_withUnnecessaryContext()
  {
    final ReplicantContext context = Replicant.context();
    final IllegalStateException exception =
      expectThrows( IllegalStateException.class, () -> Converger.create( context ) );
    assertEquals( exception.getMessage(),
                  "Replicant-0134: ReplicantService passed a context but Replicant.areZonesEnabled() is false" );
  }

  @Test
  public void getReplicantContext()
  {
    final ReplicantContext context = Replicant.context();
    final Converger converger = context.getConverger();
    assertEquals( converger.getReplicantContext(), context );
    assertEquals( getFieldValue( converger, "_context" ), null );
  }

  @Test
  public void getReplicantContext_zonesEnabled()
  {
    ReplicantTestUtil.enableZones();
    ReplicantTestUtil.resetState();

    final ReplicantContext context = Replicant.context();
    final Converger converger = context.getConverger();
    assertEquals( converger.getReplicantContext(), context );
    assertEquals( getFieldValue( converger, "_context" ), context );
  }

  @Test
  public void preConvergeAction()
  {
    final Converger c = Replicant.context().getConverger();

    // Pause scheduler so Autoruns don't auto-converge
    Arez.context().pauseScheduler();

    // should do nothing ...
    Arez.context().safeAction( c::preConverge );

    final AtomicInteger callCount = new AtomicInteger();

    Arez.context().safeAction( () -> c.setPreConvergeAction( callCount::incrementAndGet ) );

    Arez.context().safeAction( c::preConverge );

    assertEquals( callCount.get(), 1 );

    Arez.context().safeAction( c::preConverge );

    assertEquals( callCount.get(), 2 );

    Arez.context().safeAction( () -> c.setPreConvergeAction( null ) );

    Arez.context().safeAction( c::preConverge );

    assertEquals( callCount.get(), 2 );
  }

  @Test
  public void convergeCompleteAction()
  {
    final Converger c = Replicant.context().getConverger();

    // Pause scheduler so Autoruns don't auto-converge
    Arez.context().pauseScheduler();

    // should do nothing ...
    Arez.context().safeAction( c::convergeComplete );

    final AtomicInteger callCount = new AtomicInteger();

    Arez.context().safeAction( () -> c.setConvergeCompleteAction( callCount::incrementAndGet ) );

    Arez.context().safeAction( c::convergeComplete );

    assertEquals( callCount.get(), 1 );

    Arez.context().safeAction( c::convergeComplete );

    assertEquals( callCount.get(), 2 );

    Arez.context().safeAction( () -> c.setConvergeCompleteAction( null ) );

    Arez.context().safeAction( c::convergeComplete );

    assertEquals( callCount.get(), 2 );
  }

  @Test
  public void canGroup()
  {
    final Converger c = Replicant.context().getConverger();

    // Pause scheduler so Autoruns don't auto-converge
    Arez.context().pauseScheduler();

    Arez.context().safeAction( () -> {
      final ChannelAddress address = new ChannelAddress( G.G1 );
      final AreaOfInterest areaOfInterest = Replicant.context().createOrUpdateAreaOfInterest( address, null );

      assertTrue( c.canGroup( areaOfInterest,
                              AreaOfInterestRequest.Type.ADD,
                              areaOfInterest,
                              AreaOfInterestRequest.Type.ADD ) );
      assertFalse( c.canGroup( areaOfInterest,
                               AreaOfInterestRequest.Type.ADD,
                               areaOfInterest,
                               AreaOfInterestRequest.Type.UPDATE ) );
      assertFalse( c.canGroup( areaOfInterest,
                               AreaOfInterestRequest.Type.ADD,
                               areaOfInterest,
                               AreaOfInterestRequest.Type.REMOVE ) );
      assertFalse( c.canGroup( areaOfInterest,
                               AreaOfInterestRequest.Type.UPDATE,
                               areaOfInterest,
                               AreaOfInterestRequest.Type.ADD ) );
      assertTrue( c.canGroup( areaOfInterest,
                              AreaOfInterestRequest.Type.UPDATE,
                              areaOfInterest,
                              AreaOfInterestRequest.Type.UPDATE ) );
      assertFalse( c.canGroup( areaOfInterest,
                               AreaOfInterestRequest.Type.UPDATE,
                               areaOfInterest,
                               AreaOfInterestRequest.Type.REMOVE ) );
      assertFalse( c.canGroup( areaOfInterest,
                               AreaOfInterestRequest.Type.REMOVE,
                               areaOfInterest,
                               AreaOfInterestRequest.Type.ADD ) );
      assertFalse( c.canGroup( areaOfInterest,
                               AreaOfInterestRequest.Type.REMOVE,
                               areaOfInterest,
                               AreaOfInterestRequest.Type.UPDATE ) );
      assertTrue( c.canGroup( areaOfInterest,
                              AreaOfInterestRequest.Type.REMOVE,
                              areaOfInterest,
                              AreaOfInterestRequest.Type.REMOVE ) );

      final ChannelAddress channel2 = new ChannelAddress( G.G1, 2 );
      final AreaOfInterest areaOfInterest2 = Replicant.context().createOrUpdateAreaOfInterest( channel2, null );
      assertTrue( c.canGroup( areaOfInterest,
                              AreaOfInterestRequest.Type.ADD,
                              areaOfInterest2,
                              AreaOfInterestRequest.Type.ADD ) );

      final ChannelAddress address3 = new ChannelAddress( G.G2, 1 );
      final AreaOfInterest areaOfInterest3 = Replicant.context().createOrUpdateAreaOfInterest( address3, null );
      assertFalse( c.canGroup( areaOfInterest,
                               AreaOfInterestRequest.Type.ADD,
                               areaOfInterest3,
                               AreaOfInterestRequest.Type.ADD ) );
      assertFalse( c.canGroup( areaOfInterest,
                               AreaOfInterestRequest.Type.ADD,
                               areaOfInterest3,
                               AreaOfInterestRequest.Type.UPDATE ) );
      assertFalse( c.canGroup( areaOfInterest,
                               AreaOfInterestRequest.Type.ADD,
                               areaOfInterest3,
                               AreaOfInterestRequest.Type.REMOVE ) );

      final ChannelAddress address4 = new ChannelAddress( G.G1, 1 );
      final AreaOfInterest areaOfInterest4 = Replicant.context().createOrUpdateAreaOfInterest( address4, "Filter" );
      assertFalse( c.canGroup( areaOfInterest,
                               AreaOfInterestRequest.Type.ADD,
                               areaOfInterest4,
                               AreaOfInterestRequest.Type.ADD ) );
      areaOfInterest.setFilter( "Filter" );
      assertTrue( c.canGroup( areaOfInterest,
                              AreaOfInterestRequest.Type.ADD,
                              areaOfInterest4,
                              AreaOfInterestRequest.Type.ADD ) );
    } );
  }

  @Test
  public void removeOrphanSubscription()
  {
    final ReplicantContext context = Replicant.context();

    final TestConnector connector = TestConnector.create( G.class );
    connector.setConnection( new Connection( connector, ValueUtil.randomString() ) );
    final ChannelAddress address = new ChannelAddress( G.G1 );

    // Pause scheduler so Autoruns don't auto-converge
    Arez.context().pauseScheduler();

    Arez.context().safeAction( () -> {

      final Subscription subscription = context.createSubscription( address, null, true );

      connector.setState( ConnectorState.CONNECTED );

      final TestSpyEventHandler handler = registerTestSpyEventHandler();

      context.getConverger().removeOrphanSubscriptions();

      final List<AreaOfInterestRequest> requests = connector.ensureConnection().getPendingAreaOfInterestRequests();
      assertEquals( requests.size(), 1 );
      final AreaOfInterestRequest request = requests.get( 0 );
      assertEquals( request.getType(), AreaOfInterestRequest.Type.REMOVE );
      assertEquals( request.getAddress(), address );

      handler.assertEventCount( 2 );

      handler.assertNextEvent( SubscriptionOrphanedEvent.class,
                               e -> assertEquals( e.getSubscription(), subscription ) );
      handler.assertNextEvent( UnsubscribeRequestQueuedEvent.class,
                               e -> assertEquals( e.getAddress(), address ) );
    } );
  }

  @Test
  public void removeOrphanSubscription_whenManyPresent()
  {
    final ReplicantContext context = Replicant.context();

    final TestConnector connector = TestConnector.create( G.class );
    connector.setConnection( new Connection( connector, ValueUtil.randomString() ) );
    final ChannelAddress address = new ChannelAddress( G.G1 );

    // Pause scheduler so Autoruns don't auto-converge
    Arez.context().pauseScheduler();

    Arez.context().safeAction( () -> {

      context.createOrUpdateAreaOfInterest( new ChannelAddress( G.G2, 1 ), null );
      context.createOrUpdateAreaOfInterest( new ChannelAddress( G.G2, 2 ), null );
      context.createOrUpdateAreaOfInterest( new ChannelAddress( G.G2, 3 ), null );
      context.createOrUpdateAreaOfInterest( new ChannelAddress( G.G2, 4 ), null );
      context.createOrUpdateAreaOfInterest( new ChannelAddress( G.G2, 5 ), null );

      final Subscription subscription = context.createSubscription( address, null, true );

      connector.setState( ConnectorState.CONNECTED );

      final TestSpyEventHandler handler = registerTestSpyEventHandler();

      context.getConverger().removeOrphanSubscriptions();

      final List<AreaOfInterestRequest> requests = connector.ensureConnection().getPendingAreaOfInterestRequests();
      assertEquals( requests.size(), 1 );
      final AreaOfInterestRequest request = requests.get( 0 );
      assertEquals( request.getType(), AreaOfInterestRequest.Type.REMOVE );
      assertEquals( request.getAddress(), address );

      handler.assertEventCount( 2 );

      handler.assertNextEvent( SubscriptionOrphanedEvent.class,
                               e -> assertEquals( e.getSubscription(), subscription ) );
      handler.assertNextEvent( UnsubscribeRequestQueuedEvent.class,
                               e -> assertEquals( e.getAddress(), address ) );
    } );
  }

  @Test
  public void removeOrphanSubscriptions_whenConnectorDisconnected()
  {
    final ReplicantContext context = Replicant.context();

    final TestConnector connector = TestConnector.create( G.class );
    final ChannelAddress address = new ChannelAddress( G.G1 );

    // Pause scheduler so Autoruns don't auto-converge
    Arez.context().pauseScheduler();

    Arez.context().safeAction( () -> {

      context.createSubscription( address, null, true );

      connector.setState( ConnectorState.DISCONNECTED );

      final TestSpyEventHandler handler = registerTestSpyEventHandler();

      context.getConverger().removeOrphanSubscriptions();

      handler.assertEventCount( 0 );
    } );
  }

  @Test
  public void removeOrphanSubscriptions_whenSubscriptionImplicit()
  {
    final ReplicantContext context = Replicant.context();

    final TestConnector connector = TestConnector.create( G.class );
    final ChannelAddress address = new ChannelAddress( G.G1 );

    // Pause scheduler so Autoruns don't auto-converge
    Arez.context().pauseScheduler();

    Arez.context().safeAction( () -> {

      context.createSubscription( address, null, false );

      connector.setState( ConnectorState.CONNECTED );

      final TestSpyEventHandler handler = registerTestSpyEventHandler();

      context.getConverger().removeOrphanSubscriptions();

      handler.assertEventCount( 0 );
    } );
  }

  @Test
  public void removeOrphanSubscriptions_whenSubscriptionExpected()
  {
    final ReplicantContext context = Replicant.context();

    final TestConnector connector = TestConnector.create( G.class );
    final ChannelAddress address = new ChannelAddress( G.G1 );

    // Pause scheduler so Autoruns don't auto-converge
    Arez.context().pauseScheduler();

    Arez.context().safeAction( () -> {

      // Add expectation
      context.createOrUpdateAreaOfInterest( address, null );

      context.createSubscription( address, null, true );

      connector.setState( ConnectorState.CONNECTED );

      final TestSpyEventHandler handler = registerTestSpyEventHandler();

      context.getConverger().removeOrphanSubscriptions();

      handler.assertEventCount( 0 );
    } );
  }

  @Test
  public void removeOrphanSubscriptions_whenRemoveIsPending()
  {
    final ReplicantContext context = Replicant.context();

    final TestConnector connector = TestConnector.create( G.class );
    connector.setConnection( new Connection( connector, ValueUtil.randomString() ) );

    final ChannelAddress address = new ChannelAddress( G.G1 );

    // Enqueue remove request
    connector.requestUnsubscribe( address );

    // Pause scheduler so Autoruns don't auto-converge
    Arez.context().pauseScheduler();

    Arez.context().safeAction( () -> {

      context.createSubscription( address, null, true );

      connector.setState( ConnectorState.CONNECTED );

      final TestSpyEventHandler handler = registerTestSpyEventHandler();

      context.getConverger().removeOrphanSubscriptions();

      handler.assertEventCount( 0 );
    } );
  }

  @Test
  public void convergeAreaOfInterest()
  {
    final ArezContext context = Arez.context();
    final ReplicantContext rContext = Replicant.context();

    // Pause schedule so can manually interact with converger
    context.pauseScheduler();

    final TestConnector connector = TestConnector.create( ConnectorTest.G.class );
    connector.setConnection( new Connection( connector, ValueUtil.randomString() ) );
    context.safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final ChannelAddress address = new ChannelAddress( ConnectorTest.G.G1 );
    final AreaOfInterest areaOfInterest =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address, null ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Converger.Action result = context.safeAction( () -> rContext.getConverger()
      .convergeAreaOfInterest( areaOfInterest, null, null, true ) );

    assertEquals( result, Converger.Action.SUBMITTED_ADD );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscribeRequestQueuedEvent.class, e -> assertEquals( e.getAddress(), address ) );
  }

  @Test
  public void convergeAreaOfInterest_alreadySubscribed()
  {
    final ArezContext context = Arez.context();
    final ReplicantContext rContext = Replicant.context();

    // Pause schedule so can manually interact with converger
    context.pauseScheduler();

    final TestConnector connector = TestConnector.create( ConnectorTest.G.class );
    connector.setConnection( new Connection( connector, ValueUtil.randomString() ) );
    context.safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final ChannelAddress address = new ChannelAddress( ConnectorTest.G.G1 );
    final AreaOfInterest areaOfInterest =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address, null ) );
    context.safeAction( () -> rContext.createSubscription( address, null, true ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Converger.Action result = context.safeAction( () -> rContext.getConverger()
      .convergeAreaOfInterest( areaOfInterest, null, null, true ) );

    assertEquals( result, Converger.Action.NO_ACTION );

    handler.assertEventCount( 0 );
  }

  @Test
  public void convergeAreaOfInterest_subscribing()
  {
    final ArezContext context = Arez.context();
    final ReplicantContext rContext = Replicant.context();

    // Pause schedule so can manually interact with converger
    context.pauseScheduler();

    final TestConnector connector = TestConnector.create( ConnectorTest.G.class );
    final Connection connection = new Connection( connector, ValueUtil.randomString() );
    connector.setConnection( connection );
    context.safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final ChannelAddress address = new ChannelAddress( ConnectorTest.G.G1 );
    final AreaOfInterest areaOfInterest =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address, null ) );

    connection.injectCurrentAreaOfInterestRequest( new AreaOfInterestRequest( address,
                                                                              AreaOfInterestRequest.Type.ADD,
                                                                              null ) );
    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Converger.Action result = context.safeAction( () -> rContext.getConverger()
      .convergeAreaOfInterest( areaOfInterest, null, null, true ) );

    assertEquals( result, Converger.Action.IN_PROGRESS );

    handler.assertEventCount( 0 );
  }

  @Test
  public void convergeAreaOfInterest_addPending()
  {
    final ArezContext context = Arez.context();
    final ReplicantContext rContext = Replicant.context();

    // Pause schedule so can manually interact with converger
    context.pauseScheduler();

    final TestConnector connector = TestConnector.create( ConnectorTest.G.class );
    final Connection connection = new Connection( connector, ValueUtil.randomString() );
    connector.setConnection( connection );
    context.safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final ChannelAddress address = new ChannelAddress( ConnectorTest.G.G1 );
    final AreaOfInterest areaOfInterest =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address, null ) );

    connector.requestSubscribe( address, null );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Converger.Action result = context.safeAction( () -> rContext.getConverger()
      .convergeAreaOfInterest( areaOfInterest, null, null, true ) );

    assertEquals( result, Converger.Action.IN_PROGRESS );

    handler.assertEventCount( 0 );
  }

  @Test
  public void convergeAreaOfInterest_updatePending()
  {
    final ArezContext context = Arez.context();
    final ReplicantContext rContext = Replicant.context();

    // Pause schedule so can manually interact with converger
    context.pauseScheduler();

    final TestConnector connector = TestConnector.create( ConnectorTest.G.class );
    final Connection connection = new Connection( connector, ValueUtil.randomString() );
    connector.setConnection( connection );
    context.safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final ChannelAddress address = new ChannelAddress( ConnectorTest.G.G1 );
    final Object filter = ValueUtil.randomString();
    final AreaOfInterest areaOfInterest =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address, filter ) );
    context.safeAction( () -> rContext.createSubscription( address, ValueUtil.randomString(), true ) );

    connector.requestSubscriptionUpdate( address, filter );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Converger.Action result = context.safeAction( () -> rContext.getConverger()
      .convergeAreaOfInterest( areaOfInterest, null, null, true ) );

    assertEquals( result, Converger.Action.IN_PROGRESS );

    handler.assertEventCount( 0 );
  }

  @Test
  public void convergeAreaOfInterest_requestSubscriptionUpdate()
  {
    final ArezContext context = Arez.context();
    final ReplicantContext rContext = Replicant.context();

    // Pause schedule so can manually interact with converger
    context.pauseScheduler();

    final TestConnector connector = TestConnector.create( ConnectorTest.G.class );
    connector.setConnection( new Connection( connector, ValueUtil.randomString() ) );
    context.safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final ChannelAddress address = new ChannelAddress( ConnectorTest.G.G1 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterest areaOfInterest =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address, filter ) );
    context.safeAction( () -> rContext.createSubscription( address, ValueUtil.randomString(), true ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Converger.Action result = context.safeAction( () -> rContext.getConverger()
      .convergeAreaOfInterest( areaOfInterest, null, null, true ) );

    assertEquals( result, Converger.Action.SUBMITTED_UPDATE );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscriptionUpdateRequestQueuedEvent.class, e -> {
      assertEquals( e.getAddress(), address );
      assertEquals( e.getFilter(), filter );
    } );
  }

  @Test
  public void convergeAreaOfInterest_disposedAreaOfInterest()
  {
    final ArezContext context = Arez.context();
    final ReplicantContext rContext = Replicant.context();

    // Pause schedule so can manually interact with converger
    context.pauseScheduler();

    final TestConnector connector = TestConnector.create( ConnectorTest.G.class );
    connector.setConnection( new Connection( connector, ValueUtil.randomString() ) );
    context.safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final ChannelAddress address = new ChannelAddress( ConnectorTest.G.G1 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterest areaOfInterest =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address, filter ) );

    Disposable.dispose( areaOfInterest );

    final IllegalStateException exception =
      expectThrows( IllegalStateException.class,
                    () -> context.safeAction( () -> rContext.getConverger()
                      .convergeAreaOfInterest( areaOfInterest, null, null, true ) ) );

    assertEquals( exception.getMessage(),
                  "Replicant-0020: Invoked convergeAreaOfInterest() with disposed AreaOfInterest." );
  }

  @Test
  public void convergeAreaOfInterest_subscribedButRemovePending()
  {
    final ArezContext context = Arez.context();
    final ReplicantContext rContext = Replicant.context();

    // Pause schedule so can manually interact with converger
    context.pauseScheduler();

    final TestConnector connector = TestConnector.create( ConnectorTest.G.class );
    connector.setConnection( new Connection( connector, ValueUtil.randomString() ) );
    context.safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final ChannelAddress address = new ChannelAddress( ConnectorTest.G.G1 );
    final AreaOfInterest areaOfInterest =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address, null ) );
    context.safeAction( () -> rContext.createSubscription( address, null, true ) );
    connector.requestUnsubscribe( address );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Converger.Action result = context.safeAction( () -> rContext.getConverger()
      .convergeAreaOfInterest( areaOfInterest, null, null, true ) );

    assertEquals( result, Converger.Action.SUBMITTED_ADD );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscribeRequestQueuedEvent.class, e -> assertEquals( e.getAddress(), address ) );
  }

  @Test
  public void convergeAreaOfInterest_groupingAdd()
  {
    final ArezContext context = Arez.context();
    final ReplicantContext rContext = Replicant.context();

    // Pause schedule so can manually interact with converger
    context.pauseScheduler();

    final TestConnector connector = TestConnector.create( ConnectorTest.G.class );
    connector.setConnection( new Connection( connector, ValueUtil.randomString() ) );
    context.safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final ChannelAddress address1 = new ChannelAddress( ConnectorTest.G.G2, 1 );
    final ChannelAddress address2 = new ChannelAddress( ConnectorTest.G.G2, 2 );
    final AreaOfInterest areaOfInterest1 =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address1, null ) );
    final AreaOfInterest areaOfInterest2 =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address2, null ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Converger.Action result = context.safeAction( () -> rContext.getConverger()
      .convergeAreaOfInterest( areaOfInterest2, areaOfInterest1, AreaOfInterestRequest.Type.ADD, true ) );

    assertEquals( result, Converger.Action.SUBMITTED_ADD );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscribeRequestQueuedEvent.class, e -> assertEquals( e.getAddress(), address2 ) );
  }

  @Test
  public void convergeAreaOfInterest_typeDiffers()
  {
    final ArezContext context = Arez.context();
    final ReplicantContext rContext = Replicant.context();

    // Pause schedule so can manually interact with converger
    context.pauseScheduler();

    final TestConnector connector = TestConnector.create( ConnectorTest.G.class );
    connector.setConnection( new Connection( connector, ValueUtil.randomString() ) );
    context.safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final ChannelAddress address1 = new ChannelAddress( ConnectorTest.G.G2, 1 );
    final ChannelAddress address2 = new ChannelAddress( ConnectorTest.G.G2, 2 );
    final AreaOfInterest areaOfInterest1 =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address1, null ) );

    // areaOfInterest2 would actually require an update as already present
    final AreaOfInterest areaOfInterest2 =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address2, null ) );
    context.safeAction( () -> rContext.createSubscription( address2, ValueUtil.randomString(), true ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Converger.Action result = context.safeAction( () -> rContext.getConverger()
      .convergeAreaOfInterest( areaOfInterest2, areaOfInterest1, AreaOfInterestRequest.Type.ADD, true ) );

    assertEquals( result, Converger.Action.NO_ACTION );

    handler.assertEventCount( 0 );
  }

  @Test
  public void convergeAreaOfInterest_noGroupingAdd()
  {
    final ArezContext context = Arez.context();
    final ReplicantContext rContext = Replicant.context();

    // Pause schedule so can manually interact with converger
    context.pauseScheduler();

    final TestConnector connector = TestConnector.create( ConnectorTest.G.class );
    connector.setConnection( new Connection( connector, ValueUtil.randomString() ) );
    context.safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final ChannelAddress address1 = new ChannelAddress( ConnectorTest.G.G2, 1 );
    final ChannelAddress address2 = new ChannelAddress( ConnectorTest.G.G2, 2 );
    final AreaOfInterest areaOfInterest1 =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address1, null ) );
    final AreaOfInterest areaOfInterest2 =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address2, null ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Converger.Action result = context.safeAction( () -> rContext.getConverger()
      .convergeAreaOfInterest( areaOfInterest2, areaOfInterest1, AreaOfInterestRequest.Type.ADD, false ) );

    assertEquals( result, Converger.Action.TERMINATE );

    handler.assertEventCount( 0 );
  }

  @Test
  public void convergeAreaOfInterest_FilterDiffers()
  {
    final ArezContext context = Arez.context();
    final ReplicantContext rContext = Replicant.context();

    // Pause schedule so can manually interact with converger
    context.pauseScheduler();

    final TestConnector connector = TestConnector.create( ConnectorTest.G.class );
    connector.setConnection( new Connection( connector, ValueUtil.randomString() ) );
    context.safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final ChannelAddress address1 = new ChannelAddress( ConnectorTest.G.G2, 1 );
    final ChannelAddress address2 = new ChannelAddress( ConnectorTest.G.G2, 2 );
    final AreaOfInterest areaOfInterest1 =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address1, ValueUtil.randomString() ) );
    final AreaOfInterest areaOfInterest2 =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address2, ValueUtil.randomString() ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Converger.Action result = context.safeAction( () -> rContext.getConverger()
      .convergeAreaOfInterest( areaOfInterest2, areaOfInterest1, AreaOfInterestRequest.Type.ADD, true ) );

    assertEquals( result, Converger.Action.NO_ACTION );

    handler.assertEventCount( 0 );
  }

  @Test
  public void convergeAreaOfInterest_ChannelDiffers()
  {
    final ArezContext context = Arez.context();
    final ReplicantContext rContext = Replicant.context();

    // Pause schedule so can manually interact with converger
    context.pauseScheduler();

    final TestConnector connector = TestConnector.create( ConnectorTest.G.class );
    connector.setConnection( new Connection( connector, ValueUtil.randomString() ) );
    context.safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final ChannelAddress address1 = new ChannelAddress( ConnectorTest.G.G2, 1 );
    final ChannelAddress address2 = new ChannelAddress( ConnectorTest.G.G1 );
    final AreaOfInterest areaOfInterest1 =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address1, null ) );
    final AreaOfInterest areaOfInterest2 =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address2, null ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Converger.Action result = context.safeAction( () -> rContext.getConverger()
      .convergeAreaOfInterest( areaOfInterest2, areaOfInterest1, AreaOfInterestRequest.Type.ADD, true ) );

    assertEquals( result, Converger.Action.NO_ACTION );

    handler.assertEventCount( 0 );
  }

  @Test
  public void convergeAreaOfInterest_groupingUpdate()
  {
    final ArezContext context = Arez.context();
    final ReplicantContext rContext = Replicant.context();

    // Pause schedule so can manually interact with converger
    context.pauseScheduler();

    final TestConnector connector = TestConnector.create( ConnectorTest.G.class );
    connector.setConnection( new Connection( connector, ValueUtil.randomString() ) );
    context.safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final ChannelAddress address1 = new ChannelAddress( ConnectorTest.G.G2, 1 );
    final ChannelAddress address2 = new ChannelAddress( ConnectorTest.G.G2, 2 );

    final String filterOld = ValueUtil.randomString();
    final String filterNew = ValueUtil.randomString();

    final AreaOfInterest areaOfInterest1 =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address1, filterNew ) );
    context.safeAction( () -> rContext.createSubscription( address1, filterOld, true ) );
    final AreaOfInterest areaOfInterest2 =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address2, filterNew ) );
    context.safeAction( () -> rContext.createSubscription( address2, filterOld, true ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Converger.Action result = context.safeAction( () -> rContext.getConverger()
      .convergeAreaOfInterest( areaOfInterest2, areaOfInterest1, AreaOfInterestRequest.Type.UPDATE, true ) );

    assertEquals( result, Converger.Action.SUBMITTED_UPDATE );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscriptionUpdateRequestQueuedEvent.class,
                             e -> assertEquals( e.getAddress(), address2 ) );
  }

  @Test
  public void convergeAreaOfInterest_typeDiffersForUpdate()
  {
    final ArezContext context = Arez.context();
    final ReplicantContext rContext = Replicant.context();

    // Pause schedule so can manually interact with converger
    context.pauseScheduler();

    final TestConnector connector = TestConnector.create( ConnectorTest.G.class );
    connector.setConnection( new Connection( connector, ValueUtil.randomString() ) );
    context.safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final ChannelAddress address1 = new ChannelAddress( ConnectorTest.G.G2, 1 );
    final ChannelAddress address2 = new ChannelAddress( ConnectorTest.G.G2, 2 );

    final String filterOld = ValueUtil.randomString();
    final String filterNew = ValueUtil.randomString();

    final AreaOfInterest areaOfInterest1 =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address1, filterNew ) );
    context.safeAction( () -> rContext.createSubscription( address1, filterOld, true ) );
    final AreaOfInterest areaOfInterest2 =
      context.safeAction( () -> rContext.createOrUpdateAreaOfInterest( address2, filterNew ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Converger.Action result = context.safeAction( () -> rContext.getConverger()
      .convergeAreaOfInterest( areaOfInterest2, areaOfInterest1, AreaOfInterestRequest.Type.UPDATE, true ) );

    assertEquals( result, Converger.Action.NO_ACTION );

    handler.assertEventCount( 0 );
  }

  private enum G
  {
    G1, G2
  }
}
