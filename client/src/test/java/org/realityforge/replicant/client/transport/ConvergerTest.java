package org.realityforge.replicant.client.transport;

import arez.Arez;
import arez.Disposable;
import java.util.HashSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.testng.IHookCallBack;
import org.testng.IHookable;
import org.testng.ITestResult;
import org.testng.annotations.Test;
import replicant.AbstractReplicantTest;
import replicant.AreaOfInterest;
import replicant.ChannelAddress;
import replicant.Connector;
import replicant.ConnectorState;
import replicant.Replicant;
import replicant.ReplicantRuntime;
import replicant.Subscription;
import replicant.TestSpyEventHandler;
import replicant.spy.SubscriptionOrphanedEvent;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class ConvergerTest
  extends AbstractReplicantTest
  implements IHookable
{
  private enum TestSystemA
  {
    A, B
  }

  @Override
  public void run( final IHookCallBack callBack, final ITestResult testResult )
  {
    Arez.context().safeAction( () -> callBack.runTestMethod( testResult ) );
  }

  @Test
  public void preConvergeAction()
  {
    final Converger c = Converger.create( mock( ReplicantRuntime.class ) );

    // should do nothing ... particularly not crash
    c.preConverge();

    final Runnable action = mock( Runnable.class );

    c.setPreConvergeAction( action );

    c.preConverge();

    verify( action ).run();

    c.preConverge();

    verify( action, times( 2 ) ).run();

    c.setPreConvergeAction( null );

    c.preConverge();

    verify( action, times( 2 ) ).run();
  }

  @Test
  public void removeOrphanSubscription()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final Connector service = mock( Connector.class );
    final ChannelAddress address = new ChannelAddress( TestSystemA.A );

    final Subscription subscription = Replicant.context().createSubscription( address, null, true );

    final Converger c = Converger.create( clientSystem );

    when( clientSystem.getConnector( TestSystemA.class ) ).
      thenReturn( service );

    when( service.getState() ).thenReturn( ConnectorState.CONNECTED );

    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address, null ) ).
      thenReturn( -1 );

    final TestSpyEventHandler handler = new TestSpyEventHandler();
    Replicant.context().getSpy().addSpyEventHandler( handler );

    c.removeOrphanSubscription( address );

    verify( service ).requestUnsubscribe( address );

    handler.assertEventCount( 1 );

    handler.assertNextEvent( SubscriptionOrphanedEvent.class, e -> assertEquals( e.getSubscription(), subscription ) );
  }

  @Test
  public void removeOrphanSubscription_DISCONNECTED()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final Connector service = mock( Connector.class );
    final ChannelAddress address = new ChannelAddress( TestSystemA.A );

    final Converger c = Converger.create( clientSystem );

    when( clientSystem.getConnector( TestSystemA.class ) ).
      thenReturn( service );

    when( service.getState() ).thenReturn( ConnectorState.DISCONNECTED );

    c.removeOrphanSubscription( address );

    verify( service, never() ).requestUnsubscribe( address );
  }

  @Test
  public void removeOrphanSubscription_AOI_Pending()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final Connector service = mock( Connector.class );
    final ChannelAddress address = new ChannelAddress( TestSystemA.A );

    final Converger c = Converger.create( clientSystem );

    when( clientSystem.getConnector( TestSystemA.class ) ).
      thenReturn( service );

    when( service.getState() ).thenReturn( ConnectorState.CONNECTED );

    when( service.isAreaOfInterestActionPending( AreaOfInterestAction.REMOVE, address, null ) ).
      thenReturn( Boolean.TRUE );

    c.removeOrphanSubscription( address );

    verify( service, never() ).requestUnsubscribe( address );
  }

  @Test
  public void removeSubscriptionIfOrphan()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final Connector service = mock( Connector.class );
    final ChannelAddress address = new ChannelAddress( TestSystemA.A );

    final Converger c = Converger.create( clientSystem );

    when( clientSystem.getConnector( TestSystemA.class ) ).
      thenReturn( service );

    when( service.getState() ).thenReturn( ConnectorState.CONNECTED );

    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address, null ) ).
      thenReturn( -1 );

    final HashSet<ChannelAddress> expected = new HashSet<>();

    final Subscription subscription = Replicant.context().createSubscription( address, null, true );

    c.removeSubscriptionIfOrphan( expected, subscription );

    verify( service ).requestUnsubscribe( address );
  }

  @Test
  public void removeSubscriptionIfOrphan_expected()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final Connector service = mock( Connector.class );
    final ChannelAddress address = new ChannelAddress( TestSystemA.A );
    final HashSet<ChannelAddress> expected = new HashSet<>();
    expected.add( address );

    final Converger c = Converger.create( clientSystem );

    when( clientSystem.getConnector( address.getSystem() ) ).
      thenReturn( service );

    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address, null ) ).
      thenReturn( -1 );

    final Subscription subscription = Replicant.context().createSubscription( address, null, true );
    c.removeSubscriptionIfOrphan( expected, subscription );

    verify( clientSystem, never() ).getConnector( TestSystemA.class );
  }

  @Test
  public void removeSubscriptionIfOrphan_notExplicit()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final ChannelAddress address = new ChannelAddress( TestSystemA.A );

    final Converger c = Converger.create( clientSystem );

    final HashSet<ChannelAddress> expected = new HashSet<>();

    final Subscription subscription = Replicant.context().createSubscription( address, null, false );

    c.removeSubscriptionIfOrphan( expected, subscription );

    verify( clientSystem, never() ).getConnector( TestSystemA.class );
  }

  @Test
  public void removeOrphanSubscriptions()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final Connector service = mock( Connector.class );
    final ChannelAddress address = new ChannelAddress( TestSystemA.A );

    Replicant.context().createOrUpdateAreaOfInterest( new ChannelAddress( TestSystemA.B, 1 ), null );
    Replicant.context().createOrUpdateAreaOfInterest( new ChannelAddress( TestSystemA.B, 2 ), null );
    Replicant.context().createOrUpdateAreaOfInterest( new ChannelAddress( TestSystemA.B, 3 ), null );
    Replicant.context().createOrUpdateAreaOfInterest( new ChannelAddress( TestSystemA.B, 4 ), null );
    Replicant.context().createOrUpdateAreaOfInterest( new ChannelAddress( TestSystemA.B, 5 ), null );

    final Converger c = Converger.create( clientSystem );

    when( clientSystem.getConnector( address.getSystem() ) ).
      thenReturn( service );

    when( service.getState() ).thenReturn( ConnectorState.CONNECTED );

    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address, null ) ).
      thenReturn( -1 );

    Replicant.context().createSubscription( address, null, true );

    c.removeOrphanSubscriptions();

    verify( service ).requestUnsubscribe( address );
  }

  @Test
  public void convergeSubscription()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final Connector service = mock( Connector.class );

    final ChannelAddress address = new ChannelAddress( TestSystemA.A );
    final AreaOfInterest areaOfInterest = createAreaOfInterest( address, null );

    final Converger c = Converger.create( clientSystem );

    when( clientSystem.getConnector( address.getSystem() ) ).
      thenReturn( service );

    when( service.getState() ).thenReturn( ConnectorState.CONNECTED );

    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.ADD, address, null ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address, null ) ).
      thenReturn( -1 );

    assertEquals( c.convergeAreaOfInterest( areaOfInterest, null, null, true ),
                  ConvergeAction.SUBMITTED_ADD );

    verify( service ).requestSubscribe( address, null );
    verify( service, never() ).requestUnsubscribe( address );
    verify( service, never() ).requestSubscriptionUpdate( address, null );
  }

  @Test
  public void convergeSubscription_subscribedButRemovePending()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final Connector service = mock( Connector.class );

    final ChannelAddress address = new ChannelAddress( TestSystemA.A );
    Replicant.context().createSubscription( address, null, true );
    final AreaOfInterest areaOfInterest = createAreaOfInterest( address, null );

    final Converger c = Converger.create( clientSystem );

    when( clientSystem.getConnector( address.getSystem() ) ).
      thenReturn( service );

    when( service.getState() ).thenReturn( ConnectorState.CONNECTED );

    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.ADD, address, null ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address, null ) ).
      thenReturn( 0 );

    assertEquals( c.convergeAreaOfInterest( areaOfInterest, null, null, true ),
                  ConvergeAction.SUBMITTED_ADD );

    verify( service ).requestSubscribe( address, null );
    verify( service, never() ).requestUnsubscribe( address );
    verify( service, never() ).requestSubscriptionUpdate( address, null );
  }

  @Test
  public void convergeSubscription_subscribed()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final Connector service = mock( Connector.class );

    final ChannelAddress address = new ChannelAddress( TestSystemA.A );
    Replicant.context().createSubscription( address, null, true );
    final AreaOfInterest areaOfInterest = createAreaOfInterest( address, null );

    final Converger c = Converger.create( clientSystem );

    when( clientSystem.getConnector( address.getSystem() ) ).
      thenReturn( service );

    when( service.getState() ).thenReturn( ConnectorState.CONNECTED );

    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.ADD, address, null ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address, null ) ).
      thenReturn( -1 );

    assertEquals( c.convergeAreaOfInterest( areaOfInterest, null, null, true ),
                  ConvergeAction.IN_PROGRESS );

    verify( service, never() ).requestSubscribe( address, null );
    verify( service, never() ).requestUnsubscribe( address );
    verify( service, never() ).requestSubscriptionUpdate( address, null );
  }

  @Test
  public void convergeSubscription_addPending()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final Connector service = mock( Connector.class );

    final ChannelAddress address = new ChannelAddress( TestSystemA.A );
    final AreaOfInterest areaOfInterest = createAreaOfInterest( address, null );

    final Converger c = Converger.create( clientSystem );

    when( clientSystem.getConnector( address.getSystem() ) ).
      thenReturn( service );

    when( service.getState() ).thenReturn( ConnectorState.CONNECTED );

    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.ADD, address, null ) ).
      thenReturn( 1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address, null ) ).
      thenReturn( -1 );

    assertEquals( c.convergeAreaOfInterest( areaOfInterest, null, null, true ),
                  ConvergeAction.IN_PROGRESS );

    verify( service, never() ).requestSubscribe( address, null );
    verify( service, never() ).requestUnsubscribe( address );
    verify( service, never() ).requestSubscriptionUpdate( address, null );
  }

  @Test
  public void convergeSubscription_updatePending()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final Connector service = mock( Connector.class );

    final ChannelAddress address = new ChannelAddress( TestSystemA.A );
    final AreaOfInterest areaOfInterest = createAreaOfInterest( address, "Filter1" );

    final Converger c = Converger.create( clientSystem );

    when( clientSystem.getConnector( address.getSystem() ) ).
      thenReturn( service );

    when( service.getState() ).thenReturn( ConnectorState.CONNECTED );

    Replicant.context().createSubscription( address, "Filter1", true );

    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.ADD, address, "Filter1" ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address, null ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.UPDATE, address, "Filter1" ) ).
      thenReturn( 4 );

    assertEquals( c.convergeAreaOfInterest( areaOfInterest, null, null, true ),
                  ConvergeAction.IN_PROGRESS );

    verify( service, never() ).requestSubscribe( address, "Filter1" );
    verify( service, never() ).requestUnsubscribe( address );
    verify( service, never() ).requestSubscriptionUpdate( address, "Filter1" );
  }

  @Test
  public void convergeSubscription_requestSubscriptionUpdate()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final Connector service = mock( Connector.class );

    final ChannelAddress address = new ChannelAddress( TestSystemA.A );
    Replicant.context().createSubscription( address, "OldFilter", false );
    final AreaOfInterest areaOfInterest = createAreaOfInterest( address, "Filter1" );

    final Converger c = Converger.create( clientSystem );

    when( clientSystem.getConnector( address.getSystem() ) ).
      thenReturn( service );

    when( service.getState() ).thenReturn( ConnectorState.CONNECTED );

    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.ADD, address, "Filter1" ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address, null ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.UPDATE, address, "Filter1" ) ).
      thenReturn( -1 );

    assertEquals( c.convergeAreaOfInterest( areaOfInterest, null, null, true ),
                  ConvergeAction.SUBMITTED_UPDATE );

    verify( service, never() ).requestSubscribe( address, "Filter1" );
    verify( service, never() ).requestUnsubscribe( address );
    verify( service ).requestSubscriptionUpdate( address, "Filter1" );
  }

  @Test
  public void canGroup()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );

    final Converger c = Converger.create( clientSystem );

    final ChannelAddress address = new ChannelAddress( TestSystemA.A );
    final AreaOfInterest areaOfInterest = createAreaOfInterest( address, null );

    assertTrue( c.canGroup( areaOfInterest, AreaOfInterestAction.ADD, areaOfInterest, AreaOfInterestAction.ADD ) );
    assertFalse( c.canGroup( areaOfInterest, AreaOfInterestAction.ADD, areaOfInterest, AreaOfInterestAction.UPDATE ) );
    assertFalse( c.canGroup( areaOfInterest, AreaOfInterestAction.ADD, areaOfInterest, AreaOfInterestAction.REMOVE ) );
    assertFalse( c.canGroup( areaOfInterest, AreaOfInterestAction.UPDATE, areaOfInterest, AreaOfInterestAction.ADD ) );
    assertTrue( c.canGroup( areaOfInterest,
                            AreaOfInterestAction.UPDATE,
                            areaOfInterest,
                            AreaOfInterestAction.UPDATE ) );
    assertFalse( c.canGroup( areaOfInterest,
                             AreaOfInterestAction.UPDATE,
                             areaOfInterest,
                             AreaOfInterestAction.REMOVE ) );
    assertFalse( c.canGroup( areaOfInterest, AreaOfInterestAction.REMOVE, areaOfInterest, AreaOfInterestAction.ADD ) );
    assertFalse( c.canGroup( areaOfInterest,
                             AreaOfInterestAction.REMOVE,
                             areaOfInterest,
                             AreaOfInterestAction.UPDATE ) );
    assertTrue( c.canGroup( areaOfInterest,
                            AreaOfInterestAction.REMOVE,
                            areaOfInterest,
                            AreaOfInterestAction.REMOVE ) );

    final ChannelAddress channel2 = new ChannelAddress( TestSystemA.A, 2 );
    final AreaOfInterest areaOfInterest2 = createAreaOfInterest( channel2, null );
    assertTrue( c.canGroup( areaOfInterest, AreaOfInterestAction.ADD, areaOfInterest2, AreaOfInterestAction.ADD ) );

    final ChannelAddress address3 = new ChannelAddress( TestSystemA.B, 1 );
    final AreaOfInterest areaOfInterest3 = createAreaOfInterest( address3, null );
    assertFalse( c.canGroup( areaOfInterest, AreaOfInterestAction.ADD, areaOfInterest3, AreaOfInterestAction.ADD ) );
    assertFalse( c.canGroup( areaOfInterest, AreaOfInterestAction.ADD, areaOfInterest3, AreaOfInterestAction.UPDATE ) );
    assertFalse( c.canGroup( areaOfInterest, AreaOfInterestAction.ADD, areaOfInterest3, AreaOfInterestAction.REMOVE ) );

    final ChannelAddress descriptor4 = new ChannelAddress( TestSystemA.A, 1 );
    final AreaOfInterest areaOfInterest4 = createAreaOfInterest( descriptor4, "Filter" );
    assertFalse( c.canGroup( areaOfInterest, AreaOfInterestAction.ADD, areaOfInterest4, AreaOfInterestAction.ADD ) );
    areaOfInterest.getChannel().setFilter( "Filter" );
    assertTrue( c.canGroup( areaOfInterest, AreaOfInterestAction.ADD, areaOfInterest4, AreaOfInterestAction.ADD ) );
  }

  @Test
  public void convergeSubscription_inactiveSubscription()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final Connector service = mock( Connector.class );

    final ChannelAddress address = new ChannelAddress( TestSystemA.A, 1 );
    final AreaOfInterest areaOfInterest = createAreaOfInterest( address, null );
    Disposable.dispose( areaOfInterest );

    final Converger c = Converger.create( clientSystem );

    when( clientSystem.getConnector( address.getSystem() ) ).
      thenReturn( service );

    when( service.getState() ).thenReturn( ConnectorState.CONNECTED );

    final IllegalStateException exception =
      expectThrows( IllegalStateException.class,
                    () -> c.convergeAreaOfInterest( areaOfInterest, null, null, true ) );

    assertEquals( exception.getMessage(),
                  "Replicant-0020: Invoked convergeAreaOfInterest() with disposed AreaOfInterest." );
  }

  @Test
  public void convergeSubscription_groupWithAdd()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final Connector service = mock( Connector.class );

    final ChannelAddress address = new ChannelAddress( TestSystemA.A, 1 );
    final AreaOfInterest areaOfInterest1 = createAreaOfInterest( address, null );

    final ChannelAddress address2 = new ChannelAddress( TestSystemA.A, 2 );
    final AreaOfInterest areaOfInterest2 = createAreaOfInterest( address2, null );

    final ChannelAddress address3 = new ChannelAddress( TestSystemA.A, 3 );
    final AreaOfInterest areaOfInterest3 = createAreaOfInterest( address3, null );

    final Converger c = Converger.create( clientSystem );

    when( clientSystem.getConnector( address.getSystem() ) ).
      thenReturn( service );

    when( service.getState() ).thenReturn( ConnectorState.CONNECTED );

    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.ADD, address2, null ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address2, null ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.ADD, address3, null ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address3, null ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.ADD, address3, "Filter" ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address3, "Filter" ) ).
      thenReturn( -1 );

    assertEquals( c.convergeAreaOfInterest( areaOfInterest2, areaOfInterest1, AreaOfInterestAction.ADD, true ),
                  ConvergeAction.SUBMITTED_ADD );
    verify( service ).requestSubscribe( address2, null );
    verify( service, never() ).requestUnsubscribe( address2 );
    verify( service, never() ).requestSubscriptionUpdate( address2, null );

    assertEquals( c.convergeAreaOfInterest( areaOfInterest3, areaOfInterest1, AreaOfInterestAction.ADD, false ),
                  ConvergeAction.TERMINATE );
    verify( service, never() ).requestSubscribe( address3, null );
    verify( service, never() ).requestUnsubscribe( address3 );
    verify( service, never() ).requestSubscriptionUpdate( address3, null );

    areaOfInterest3.getChannel().setFilter( "Filter" );
    assertEquals( c.convergeAreaOfInterest( areaOfInterest3, areaOfInterest1, AreaOfInterestAction.ADD, true ),
                  ConvergeAction.NO_ACTION );
    verify( service, never() ).requestSubscribe( address3, null );
    verify( service, never() ).requestUnsubscribe( address3 );
    verify( service, never() ).requestSubscriptionUpdate( address3, null );
  }

  @Test
  public void convergeSubscription_groupWithUpdate()
  {
    final ReplicantRuntime clientSystem = mock( ReplicantRuntime.class );
    final Connector service = mock( Connector.class );

    final ChannelAddress address = new ChannelAddress( TestSystemA.A, 1 );
    final AreaOfInterest areaOfInterest1 = createAreaOfInterest( address, null );

    final ChannelAddress address2 = new ChannelAddress( TestSystemA.A, 2 );
    Replicant.context().createSubscription( address2, "OldFilter", true );
    final AreaOfInterest areaOfInterest2 = createAreaOfInterest( address2, null );

    final ChannelAddress address3 = new ChannelAddress( TestSystemA.A, 3 );
    Replicant.context().createSubscription( address3, "OldFilter", true );
    final AreaOfInterest areaOfInterest3 = createAreaOfInterest( address3, null );

    final Converger c = Converger.create( clientSystem );

    when( clientSystem.getConnector( address.getSystem() ) ).
      thenReturn( service );

    when( service.getState() ).thenReturn( ConnectorState.CONNECTED );

    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.ADD, address2, null ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address2, null ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.UPDATE, address2, null ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.ADD, address3, null ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address3, null ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.UPDATE, address3, null ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.ADD, address3, "Filter" ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.REMOVE, address3, "Filter" ) ).
      thenReturn( -1 );
    when( service.indexOfPendingAreaOfInterestAction( AreaOfInterestAction.UPDATE, address3, "Filter" ) ).
      thenReturn( -1 );

    assertEquals( c.convergeAreaOfInterest( areaOfInterest2, areaOfInterest1, AreaOfInterestAction.UPDATE, true ),
                  ConvergeAction.SUBMITTED_UPDATE );
    verify( service, never() ).requestSubscribe( address2, null );
    verify( service, never() ).requestUnsubscribe( address2 );
    verify( service ).requestSubscriptionUpdate( address2, null );

    assertEquals( c.convergeAreaOfInterest( areaOfInterest3, areaOfInterest1, AreaOfInterestAction.UPDATE, false ),
                  ConvergeAction.TERMINATE );
    verify( service, never() ).requestSubscribe( address3, null );
    verify( service, never() ).requestUnsubscribe( address3 );
    verify( service, never() ).requestSubscriptionUpdate( address3, null );

    areaOfInterest3.getChannel().setFilter( "Filter" );
    assertEquals( c.convergeAreaOfInterest( areaOfInterest3, areaOfInterest1, AreaOfInterestAction.UPDATE, true ),
                  ConvergeAction.NO_ACTION );
    verify( service, never() ).requestSubscribe( address3, null );
    verify( service, never() ).requestUnsubscribe( address3 );
    verify( service, never() ).requestSubscriptionUpdate( address3, null );
  }

  @Nonnull
  private AreaOfInterest createAreaOfInterest( @Nonnull final ChannelAddress address, @Nullable final Object filter )
  {
    return Replicant.context().createOrUpdateAreaOfInterest( address, filter );
  }
}
