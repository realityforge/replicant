package replicant;

import arez.Disposable;
import arez.component.Linkable;
import arez.component.Verifiable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.intellij.lang.annotations.Language;
import org.realityforge.guiceyloops.shared.ValueUtil;
import org.testng.annotations.Test;
import replicant.messages.ChangeSet;
import replicant.messages.ChannelChange;
import replicant.messages.EntityChange;
import replicant.messages.EntityChangeData;
import replicant.spy.AreaOfInterestDisposedEvent;
import replicant.spy.AreaOfInterestStatusUpdatedEvent;
import replicant.spy.ConnectFailureEvent;
import replicant.spy.ConnectedEvent;
import replicant.spy.DisconnectFailureEvent;
import replicant.spy.DisconnectedEvent;
import replicant.spy.InSyncEvent;
import replicant.spy.MessageProcessFailureEvent;
import replicant.spy.MessageProcessedEvent;
import replicant.spy.MessageReadFailureEvent;
import replicant.spy.OutOfSyncEvent;
import replicant.spy.RestartEvent;
import replicant.spy.SubscribeCompletedEvent;
import replicant.spy.SubscribeFailedEvent;
import replicant.spy.SubscribeRequestQueuedEvent;
import replicant.spy.SubscribeStartedEvent;
import replicant.spy.SubscriptionCreatedEvent;
import replicant.spy.SubscriptionDisposedEvent;
import replicant.spy.SubscriptionUpdateCompletedEvent;
import replicant.spy.SubscriptionUpdateFailedEvent;
import replicant.spy.SubscriptionUpdateRequestQueuedEvent;
import replicant.spy.SubscriptionUpdateStartedEvent;
import replicant.spy.SyncFailureEvent;
import replicant.spy.SyncRequestEvent;
import replicant.spy.UnsubscribeCompletedEvent;
import replicant.spy.UnsubscribeFailedEvent;
import replicant.spy.UnsubscribeRequestQueuedEvent;
import replicant.spy.UnsubscribeStartedEvent;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

@SuppressWarnings( { "NonJREEmulationClassesInClientCode", "Duplicates" } )
public class ConnectorTest
  extends AbstractReplicantTest
{
  @Test
  public void construct()
    throws Exception
  {
    final Disposable schedulerLock = pauseScheduler();
    final ReplicantRuntime runtime = Replicant.context().getRuntime();

    safeAction( () -> assertEquals( runtime.getConnectors().size(), 0 ) );

    final SystemSchema schema =
      new SystemSchema( ValueUtil.randomInt(),
                        ValueUtil.randomString(),
                        new ChannelSchema[ 0 ],
                        new EntitySchema[ 0 ] );
    final Connector connector = createConnector( schema );

    assertEquals( connector.getSchema(), schema );

    safeAction( () -> assertEquals( runtime.getConnectors().size(), 1 ) );

    assertEquals( connector.getReplicantRuntime(), runtime );
    assertTrue( connector.getReplicantContext().getSchemaService().getSchemas().contains( schema ) );

    safeAction( () -> assertEquals( connector.getState(), ConnectorState.DISCONNECTED ) );

    schedulerLock.dispose();

    safeAction( () -> assertEquals( connector.getState(), ConnectorState.CONNECTING ) );
  }

  @Test
  public void dispose()
  {
    final ReplicantRuntime runtime = Replicant.context().getRuntime();

    safeAction( () -> assertEquals( runtime.getConnectors().size(), 0 ) );

    final SystemSchema schema = newSchema();
    final Connector connector = createConnector( schema );

    safeAction( () -> assertEquals( runtime.getConnectors().size(), 1 ) );
    assertTrue( connector.getReplicantContext().getSchemaService().getSchemas().contains( schema ) );

    Disposable.dispose( connector );

    safeAction( () -> assertEquals( runtime.getConnectors().size(), 0 ) );
    assertFalse( connector.getReplicantContext().getSchemaService().getSchemas().contains( schema ) );
  }

  @Test
  public void testToString()
    throws Exception
  {
    final SystemSchema schema = newSchema();
    final Connector connector = createConnector( schema );
    assertEquals( connector.toString(), "Connector[" + schema.getName() + "]" );
    ReplicantTestUtil.disableNames();
    assertEquals( connector.toString(), "replicant.Arez_Connector@" + Integer.toHexString( connector.hashCode() ) );
  }

  @Test
  public void setConnection_whenConnectorProcessingMessage()
    throws Exception
  {
    final Connector connector = createConnector();

    final Connection connection = newConnection( connector );

    pauseScheduler();
    connector.pauseMessageScheduler();

    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );

    final ChannelAddress address = new ChannelAddress( connector.getSchema().getId(), 0 );
    final Subscription subscription = createSubscription( address, null, true );

    connector.onConnection( ValueUtil.randomString() );

    // Connection not swapped yet but will do one MessageProcess completes
    assertFalse( Disposable.isDisposed( subscription ) );
    assertEquals( connector.getConnection(), connection );
    assertNotNull( connector.getPostMessageResponseAction() );
  }

  @Test
  public void setConnection_whenExistingConnection()
    throws Exception
  {
    final Connector connector = createConnector();

    final Connection connection = newConnection( connector );

    pauseScheduler();
    connector.pauseMessageScheduler();

    connector.recordLastRxRequestId( ValueUtil.randomInt() );
    connector.recordLastTxRequestId( ValueUtil.randomInt() );
    connector.recordLastSyncRxRequestId( ValueUtil.randomInt() );
    connector.recordLastSyncTxRequestId( ValueUtil.randomInt() );
    connector.recordSyncInFlight( true );
    connector.recordPendingResponseQueueEmpty( false );

    assertFalse( Disposable.isDisposed( connection ) );
    assertEquals( connector.getConnection(), connection );

    final String newConnectionId = ValueUtil.randomString();
    connector.onConnection( newConnectionId );

    assertTrue( Disposable.isDisposed( connection ) );
    assertEquals( connector.ensureConnection().getConnectionId(), newConnectionId );

    safeAction( () -> assertEquals( connector.getLastRxRequestId(), 0 ) );
    safeAction( () -> assertEquals( connector.getLastTxRequestId(), 0 ) );
    safeAction( () -> assertEquals( connector.getLastSyncRxRequestId(), 0 ) );
    safeAction( () -> assertEquals( connector.getLastSyncTxRequestId(), 0 ) );
    safeAction( () -> assertFalse( connector.isSyncInFlight() ) );
    safeAction( () -> assertTrue( connector.isPendingResponseQueueEmpty() ) );
  }

  @Test
  public void connect()
  {
    pauseScheduler();

    final Connector connector = createConnector();
    safeAction( () -> assertEquals( connector.getState(), ConnectorState.DISCONNECTED ) );

    safeAction( connector::connect );

    verify( connector.getTransport() ).connect( any( Transport.OnConnect.class ), any( Transport.OnError.class ) );

    safeAction( () -> assertEquals( connector.getState(), ConnectorState.CONNECTING ) );
  }

  @Test
  public void connect_causesError()
  {
    pauseScheduler();

    final Connector connector = createConnector();
    safeAction( () -> assertEquals( connector.getState(), ConnectorState.DISCONNECTED ) );

    reset( connector.getTransport() );

    final IllegalStateException exception = new IllegalStateException();
    doAnswer( i -> {
      throw exception;
    } ).when( connector.getTransport() ).connect( any( Transport.OnConnect.class ), any( Transport.OnError.class ) );

    final IllegalStateException actual =
      expectThrows( IllegalStateException.class, () -> safeAction( connector::connect ) );

    assertEquals( actual, exception );
    safeAction( () -> assertEquals( connector.getState(), ConnectorState.ERROR ) );

    verify( connector.getTransport() ).unbind();
  }

  @Test
  public void transportDisconnect()
  {
    pauseScheduler();

    final Connector connector = createConnector();
    newConnection( connector );

    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    connector.transportDisconnect();

    verify( connector.getTransport() ).disconnect( any( SafeProcedure.class ), any( Transport.OnError.class ) );

    safeAction( () -> assertEquals( connector.getState(), ConnectorState.DISCONNECTING ) );
  }

  @Test
  public void disconnect()
  {
    pauseScheduler();

    final Connector connector = createConnector();
    newConnection( connector );

    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    safeAction( connector::disconnect );

    verify( connector.getTransport() ).disconnect( any( SafeProcedure.class ), any( Transport.OnError.class ) );

    safeAction( () -> assertEquals( connector.getState(), ConnectorState.DISCONNECTING ) );

    verify( connector.getTransport(), never() ).unbind();
  }

  @Test
  public void disconnect_causesError()
  {
    pauseScheduler();

    final Connector connector = createConnector();
    newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    reset( connector.getTransport() );

    final IllegalStateException exception = new IllegalStateException();
    doAnswer( i -> {
      throw exception;
    } ).when( connector.getTransport() ).disconnect( any( SafeProcedure.class ), any( Transport.OnError.class ) );

    final IllegalStateException actual =
      expectThrows( IllegalStateException.class, () -> safeAction( connector::disconnect ) );

    assertEquals( actual, exception );

    safeAction( () -> assertEquals( connector.getState(), ConnectorState.ERROR ) );
    verify( connector.getTransport() ).unbind();
  }

  @Test
  public void onDisconnected()
  {
    final Connector connector = createConnector();

    newConnection( connector );

    safeAction( () -> connector.setState( ConnectorState.CONNECTING ) );

    safeAction( () -> assertEquals( connector.getReplicantRuntime().getState(),
                                    RuntimeState.CONNECTING ) );

    // Pause scheduler so runtime does not try to update state
    pauseScheduler();

    reset( connector.getTransport() );

    connector.onDisconnected();

    safeAction( () -> assertEquals( connector.getState(), ConnectorState.DISCONNECTED ) );
    safeAction( () -> assertEquals( connector.getReplicantRuntime().getState(),
                                    RuntimeState.DISCONNECTED ) );

    verify( connector.getTransport() ).unbind();
  }

  @Test
  public void onDisconnected_generatesSpyMessage()
    throws Exception
  {
    final Connector connector = createConnector();

    safeAction( () -> connector.setState( ConnectorState.CONNECTING ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    // Pause scheduler so runtime does not try to update state
    pauseScheduler();

    connector.onDisconnected();

    handler.assertEventCount( 1 );
    handler.assertNextEvent( DisconnectedEvent.class,
                             e -> assertEquals( e.getSchemaId(), connector.getSchema().getId() ) );
  }

  @Test
  public void onDisconnectFailure()
    throws Exception
  {
    final Connector connector = createConnector();

    safeAction( () -> connector.setState( ConnectorState.CONNECTING ) );

    safeAction( () -> assertEquals( connector.getReplicantRuntime().getState(),
                                    RuntimeState.CONNECTING ) );

    final Throwable error = new Throwable();

    // Pause scheduler so runtime does not try to update state
    pauseScheduler();

    connector.onDisconnectFailure( error );

    safeAction( () -> assertEquals( connector.getState(), ConnectorState.ERROR ) );
    safeAction( () -> assertEquals( connector.getReplicantRuntime().getState(),
                                    RuntimeState.ERROR ) );
  }

  @Test
  public void onDisconnectFailure_generatesSpyMessage()
    throws Exception
  {
    final Connector connector = createConnector();

    safeAction( () -> connector.setState( ConnectorState.CONNECTING ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Throwable error = new Throwable();

    // Pause scheduler so runtime does not try to update state
    pauseScheduler();

    connector.onDisconnectFailure( error );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( DisconnectFailureEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getError(), error );
    } );
  }

  @Test
  public void onConnected()
    throws Exception
  {
    final Connector connector = createConnector();
    final Connection connection = new Connection( connector, ValueUtil.randomString() );

    safeAction( () -> connector.setState( ConnectorState.CONNECTING ) );

    safeAction( () -> assertEquals( connector.getReplicantRuntime().getState(),
                                    RuntimeState.CONNECTING ) );

    // Pause scheduler so runtime does not try to update state
    pauseScheduler();

    final Field field = Connector.class.getDeclaredField( "_connection" );
    field.setAccessible( true );
    field.set( connector, connection );

    connector.onConnected();

    safeAction( () -> assertEquals( connector.getState(), ConnectorState.CONNECTED ) );
    safeAction( () -> assertEquals( connector.getReplicantRuntime().getState(),
                                    RuntimeState.CONNECTED ) );

    verify( connector.getTransport() ).bind( connection.getTransportContext(), Replicant.context() );
  }

  @Test
  public void onConnected_generatesSpyMessage()
    throws Exception
  {
    final Connector connector = createConnector();
    newConnection( connector );

    safeAction( () -> connector.setState( ConnectorState.CONNECTING ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.onConnected();

    handler.assertEventCount( 1 );
    handler.assertNextEvent( ConnectedEvent.class,
                             e -> assertEquals( e.getSchemaId(), connector.getSchema().getId() ) );
  }

  @Test
  public void onConnectFailure()
    throws Exception
  {
    final Connector connector = createConnector();

    safeAction( () -> connector.setState( ConnectorState.CONNECTING ) );

    safeAction( () -> assertEquals( connector.getReplicantRuntime().getState(),
                                    RuntimeState.CONNECTING ) );

    final Throwable error = new Throwable();

    // Pause scheduler so runtime does not try to update state
    pauseScheduler();

    connector.onConnectFailure( error );

    safeAction( () -> assertEquals( connector.getState(), ConnectorState.ERROR ) );
    safeAction( () -> assertEquals( connector.getReplicantRuntime().getState(),
                                    RuntimeState.ERROR ) );
  }

  @Test
  public void onConnectFailure_generatesSpyMessage()
    throws Exception
  {
    final Connector connector = createConnector();

    safeAction( () -> connector.setState( ConnectorState.CONNECTING ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Throwable error = new Throwable();

    // Pause scheduler so runtime does not try to update state
    pauseScheduler();

    connector.onConnectFailure( error );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( ConnectFailureEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getError(), error );
    } );
  }

  @Test
  public void onMessageReceived()
    throws Exception
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );
    final String rawJsonData = ValueUtil.randomString();

    pauseScheduler();
    connector.pauseMessageScheduler();

    assertEquals( connection.getUnparsedResponses().size(), 0 );
    assertFalse( connector.isSchedulerActive() );

    connector.onMessageReceived( rawJsonData );

    assertEquals( connection.getUnparsedResponses().size(), 1 );
    assertEquals( connection.getUnparsedResponses().get( 0 ).getRawJsonData(), rawJsonData );
    assertTrue( connector.isSchedulerActive() );
  }

  @Test
  public void onMessageProcessed()
    throws Exception
  {
    final Connector connector = createConnector();

    safeAction( () -> connector.setState( ConnectorState.CONNECTING ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final MessageResponse response = new MessageResponse( "" );
    response.recordChangeSet( ChangeSet.create( 47, null, null ), null );
    connector.onMessageProcessed( response );

    verify( connector.getTransport() ).onMessageProcessed();

    handler.assertEventCount( 1 );

    handler.assertNextEvent( MessageProcessedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getDataLoadStatus().getSequence(), 47 );
    } );
  }

  @Test
  public void onMessageProcessFailure()
    throws Exception
  {
    final Connector connector = createConnector();
    newConnection( connector );

    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final Throwable error = new Throwable();

    // Pause scheduler so runtime does not try to update state
    pauseScheduler();

    connector.onMessageProcessFailure( error );

    safeAction( () -> assertEquals( connector.getState(), ConnectorState.DISCONNECTING ) );
  }

  @Test
  public void onMessageProcessFailure_generatesSpyMessage()
    throws Exception
  {
    final Connector connector = createConnector();

    safeAction( () -> connector.setState( ConnectorState.CONNECTING ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Throwable error = new Throwable();

    connector.onMessageProcessFailure( error );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( MessageProcessFailureEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getError(), error );
    } );
  }

  @Test
  public void disconnectIfPossible()
    throws Exception
  {
    final Connector connector = createConnector();
    newConnection( connector );

    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final Throwable error = new Throwable();

    safeAction( () -> connector.disconnectIfPossible( error ) );

    safeAction( () -> assertEquals( connector.getState(), ConnectorState.DISCONNECTING ) );
  }

  @Test
  public void disconnectIfPossible_noActionAsConnecting()
    throws Exception
  {
    final Connector connector = createConnector();

    safeAction( () -> connector.setState( ConnectorState.CONNECTING ) );

    final Throwable error = new Throwable();

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    safeAction( () -> connector.disconnectIfPossible( error ) );

    handler.assertEventCount( 0 );

    safeAction( () -> assertEquals( connector.getState(), ConnectorState.CONNECTING ) );
  }

  @Test
  public void disconnectIfPossible_generatesSpyEvent()
    throws Exception
  {
    final Connector connector = createConnector();
    newConnection( connector );

    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Throwable error = new Throwable();

    safeAction( () -> connector.disconnectIfPossible( error ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( RestartEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getError(), error );
    } );
  }

  @Test
  public void onMessageReadFailure()
    throws Exception
  {
    final Connector connector = createConnector();
    newConnection( connector );

    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    final Throwable error = new Throwable();

    // Pause scheduler so runtime does not try to update state
    pauseScheduler();

    connector.onMessageReadFailure( error );

    safeAction( () -> assertEquals( connector.getState(), ConnectorState.DISCONNECTING ) );
  }

  @Test
  public void onMessageReadFailure_generatesSpyMessage()
    throws Exception
  {
    final Connector connector = createConnector();

    safeAction( () -> connector.setState( ConnectorState.CONNECTING ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final Throwable error = new Throwable();

    connector.onMessageReadFailure( error );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( MessageReadFailureEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getError(), error );
    } );
  }

  @Test
  public void onSubscribeStarted()
    throws Exception
  {
    final Connector connector = createConnector();

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final AreaOfInterest areaOfInterest =
      safeAction( () -> Replicant.context().createOrUpdateAreaOfInterest( address, null ) );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.NOT_ASKED );
    safeAction( () -> assertNull( areaOfInterest.getSubscription() ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.onSubscribeStarted( address );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.LOADING );
    safeAction( () -> assertNull( areaOfInterest.getSubscription() ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( AreaOfInterestStatusUpdatedEvent.class,
                             e -> assertEquals( e.getAreaOfInterest(), areaOfInterest ) );
    handler.assertNextEvent( SubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );
  }

  @Test
  public void onSubscribeCompleted()
    throws Exception
  {
    final Connector connector = createConnector();

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final AreaOfInterest areaOfInterest =
      safeAction( () -> Replicant.context().createOrUpdateAreaOfInterest( address, null ) );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.NOT_ASKED );
    safeAction( () -> assertNull( areaOfInterest.getSubscription() ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    final Subscription subscription = createSubscription( address, null, true );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.onSubscribeCompleted( address );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.LOADED );
    safeAction( () -> assertEquals( areaOfInterest.getSubscription(), subscription ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( AreaOfInterestStatusUpdatedEvent.class,
                             e -> assertEquals( e.getAreaOfInterest(), areaOfInterest ) );
    handler.assertNextEvent( SubscribeCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );
  }

  @Test
  public void onSubscribeFailed()
    throws Exception
  {
    final Connector connector = createConnector();

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final AreaOfInterest areaOfInterest =
      safeAction( () -> Replicant.context().createOrUpdateAreaOfInterest( address, null ) );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.NOT_ASKED );
    safeAction( () -> assertNull( areaOfInterest.getSubscription() ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    final Throwable error = new Throwable();

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.onSubscribeFailed( address, error );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.LOAD_FAILED );
    safeAction( () -> assertNull( areaOfInterest.getSubscription() ) );
    safeAction( () -> assertEquals( areaOfInterest.getError(), error ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( AreaOfInterestStatusUpdatedEvent.class,
                             e -> assertEquals( e.getAreaOfInterest(), areaOfInterest ) );
    handler.assertNextEvent( SubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
      assertEquals( e.getError(), error );
    } );
  }

  @Test
  public void onUnsubscribeStarted()
    throws Exception
  {
    final Connector connector = createConnector();

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final AreaOfInterest areaOfInterest =
      safeAction( () -> Replicant.context().createOrUpdateAreaOfInterest( address, null ) );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.NOT_ASKED );
    safeAction( () -> assertNull( areaOfInterest.getSubscription() ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    final Subscription subscription = createSubscription( address, null, true );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.onUnsubscribeStarted( address );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.UNLOADING );
    safeAction( () -> assertEquals( areaOfInterest.getSubscription(), subscription ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( AreaOfInterestStatusUpdatedEvent.class,
                             e -> assertEquals( e.getAreaOfInterest(), areaOfInterest ) );
    handler.assertNextEvent( UnsubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );
  }

  @Test
  public void onUnsubscribeCompleted()
    throws Exception
  {
    final Connector connector = createConnector();

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final AreaOfInterest areaOfInterest =
      safeAction( () -> Replicant.context().createOrUpdateAreaOfInterest( address, null ) );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.NOT_ASKED );
    safeAction( () -> assertNull( areaOfInterest.getSubscription() ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.onUnsubscribeCompleted( address );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.UNLOADED );
    safeAction( () -> assertNull( areaOfInterest.getSubscription() ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( AreaOfInterestStatusUpdatedEvent.class,
                             e -> assertEquals( e.getAreaOfInterest(), areaOfInterest ) );
    handler.assertNextEvent( UnsubscribeCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );
  }

  @Test
  public void onUnsubscribeFailed()
    throws Exception
  {
    final Connector connector = createConnector();

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final AreaOfInterest areaOfInterest =
      safeAction( () -> Replicant.context().createOrUpdateAreaOfInterest( address, null ) );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.NOT_ASKED );
    safeAction( () -> assertNull( areaOfInterest.getSubscription() ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    final Throwable error = new Throwable();

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.onUnsubscribeFailed( address, error );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.UNLOADED );
    safeAction( () -> assertNull( areaOfInterest.getSubscription() ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( AreaOfInterestStatusUpdatedEvent.class,
                             e -> assertEquals( e.getAreaOfInterest(), areaOfInterest ) );
    handler.assertNextEvent( UnsubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
      assertEquals( e.getError(), error );
    } );
  }

  @Test
  public void onSubscriptionUpdateStarted()
    throws Exception
  {
    final Connector connector = createConnector();

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final AreaOfInterest areaOfInterest =
      safeAction( () -> Replicant.context().createOrUpdateAreaOfInterest( address, null ) );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.NOT_ASKED );
    safeAction( () -> assertNull( areaOfInterest.getSubscription() ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    final Subscription subscription = createSubscription( address, null, true );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.onSubscriptionUpdateStarted( address );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.UPDATING );
    safeAction( () -> assertEquals( areaOfInterest.getSubscription(), subscription ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( AreaOfInterestStatusUpdatedEvent.class,
                             e -> assertEquals( e.getAreaOfInterest(), areaOfInterest ) );
    handler.assertNextEvent( SubscriptionUpdateStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );
  }

  @Test
  public void onSubscriptionUpdateCompleted()
    throws Exception
  {
    final Connector connector = createConnector();

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final AreaOfInterest areaOfInterest =
      safeAction( () -> Replicant.context().createOrUpdateAreaOfInterest( address, null ) );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.NOT_ASKED );
    safeAction( () -> assertNull( areaOfInterest.getSubscription() ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    final Subscription subscription = createSubscription( address, null, true );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.onSubscriptionUpdateCompleted( address );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.UPDATED );
    safeAction( () -> assertEquals( areaOfInterest.getSubscription(), subscription ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( AreaOfInterestStatusUpdatedEvent.class,
                             e -> assertEquals( e.getAreaOfInterest(), areaOfInterest ) );
    handler.assertNextEvent( SubscriptionUpdateCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );
  }

  @Test
  public void onSubscriptionUpdateFailed()
    throws Exception
  {
    final Connector connector = createConnector();

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final AreaOfInterest areaOfInterest =
      safeAction( () -> Replicant.context().createOrUpdateAreaOfInterest( address, null ) );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.NOT_ASKED );
    safeAction( () -> assertNull( areaOfInterest.getSubscription() ) );
    safeAction( () -> assertNull( areaOfInterest.getError() ) );

    final Throwable error = new Throwable();

    final Subscription subscription = createSubscription( address, null, true );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.onSubscriptionUpdateFailed( address, error );

    assertEquals( areaOfInterest.getStatus(), AreaOfInterest.Status.UPDATE_FAILED );
    safeAction( () -> assertEquals( areaOfInterest.getSubscription(), subscription ) );
    safeAction( () -> assertEquals( areaOfInterest.getError(), error ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( AreaOfInterestStatusUpdatedEvent.class,
                             e -> assertEquals( e.getAreaOfInterest(), areaOfInterest ) );
    handler.assertNextEvent( SubscriptionUpdateFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
      assertEquals( e.getError(), error );
    } );
  }

  @Test
  public void areaOfInterestRequestPendingQueries()
  {
    final Connector connector = createConnector();

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final String filter = ValueUtil.randomString();

    assertFalse( connector.isAreaOfInterestRequestPending( AreaOfInterestRequest.Type.ADD, address, filter ) );
    assertEquals( connector.lastIndexOfPendingAreaOfInterestRequest( AreaOfInterestRequest.Type.ADD, address, filter ),
                  -1 );

    final Connection connection = newConnection( connector );

    assertFalse( connector.isAreaOfInterestRequestPending( AreaOfInterestRequest.Type.ADD, address, filter ) );
    assertEquals( connector.lastIndexOfPendingAreaOfInterestRequest( AreaOfInterestRequest.Type.ADD, address, filter ),
                  -1 );

    connection.requestSubscribe( address, filter );

    assertTrue( connector.isAreaOfInterestRequestPending( AreaOfInterestRequest.Type.ADD, address, filter ) );
    assertEquals( connector.lastIndexOfPendingAreaOfInterestRequest( AreaOfInterestRequest.Type.ADD, address, filter ),
                  1 );
  }

  @Test
  public void connection()
  {
    final Connector connector = createConnector();

    assertNull( connector.getConnection() );

    final Connection connection = newConnection( connector );

    final Subscription subscription1 = createSubscription( new ChannelAddress( 1, 0 ), null, true );

    assertEquals( connector.getConnection(), connection );
    assertEquals( connector.ensureConnection(), connection );
    assertFalse( Disposable.isDisposed( subscription1 ) );

    connector.onDisconnection();

    assertNull( connector.getConnection() );
    assertTrue( Disposable.isDisposed( subscription1 ) );
  }

  @Test
  public void ensureConnection_WhenNoConnection()
  {
    final Connector connector = createConnector();

    final IllegalStateException exception = expectThrows( IllegalStateException.class, connector::ensureConnection );

    assertEquals( exception.getMessage(),
                  "Replicant-0031: Connector.ensureConnection() when no connection is present." );
  }

  @Test
  public void purgeSubscriptions()
  {
    final Connector connector1 = createConnector( newSchema( 1 ) );
    createConnector( newSchema( 2 ) );

    final Subscription subscription1 = createSubscription( new ChannelAddress( 1, 0 ), null, true );
    final Subscription subscription2 = createSubscription( new ChannelAddress( 1, 1, 2 ), null, true );
    // The next two are from a different Connector
    final Subscription subscription3 = createSubscription( new ChannelAddress( 2, 0, 1 ), null, true );
    final Subscription subscription4 = createSubscription( new ChannelAddress( 2, 0, 2 ), null, true );

    assertFalse( Disposable.isDisposed( subscription1 ) );
    assertFalse( Disposable.isDisposed( subscription2 ) );
    assertFalse( Disposable.isDisposed( subscription3 ) );
    assertFalse( Disposable.isDisposed( subscription4 ) );

    connector1.purgeSubscriptions();

    assertTrue( Disposable.isDisposed( subscription1 ) );
    assertTrue( Disposable.isDisposed( subscription2 ) );
    assertFalse( Disposable.isDisposed( subscription3 ) );
    assertFalse( Disposable.isDisposed( subscription4 ) );
  }

  @Test
  public void progressMessages()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );
    final ChannelChange[] channelChanges = { ChannelChange.create( 0, ChannelChange.Action.ADD, null ) };
    response.recordChangeSet( ChangeSet.create( ValueUtil.randomInt(), channelChanges, null ), null );

    assertNull( connector.getSchedulerLock() );

    connector.resumeMessageScheduler();

    //response needs processing of channel messages

    final boolean result0 = connector.progressMessages();

    assertTrue( result0 );
    final Disposable schedulerLock0 = connector.getSchedulerLock();
    assertNotNull( schedulerLock0 );

    //response needs worldValidated

    final boolean result1 = connector.progressMessages();

    assertTrue( result1 );
    assertNull( connector.getSchedulerLock() );
    assertTrue( Disposable.isDisposed( schedulerLock0 ) );

    final boolean result2 = connector.progressMessages();

    assertTrue( result2 );
    // Current message should be nulled and completed processing now
    assertNull( connection.getCurrentMessageResponse() );

    final boolean result3 = connector.progressMessages();

    assertFalse( result3 );
    assertNull( connector.getSchedulerLock() );
  }

  @Test
  public void progressMessages_whenConnectionHasBeenDisconnectedInMeantime()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );
    final ChannelChange[] channelChanges = { ChannelChange.create( 0, ChannelChange.Action.ADD, null ) };
    response.recordChangeSet( ChangeSet.create( ValueUtil.randomInt(), channelChanges, null ), null );

    final AtomicInteger callCount = new AtomicInteger();
    connector.setPostMessageResponseAction( callCount::incrementAndGet );

    assertNull( connector.getSchedulerLock() );
    assertEquals( callCount.get(), 0 );

    connector.resumeMessageScheduler();

    assertNull( connector.getSchedulerLock() );
    assertEquals( callCount.get(), 0 );

    assertTrue( connector.progressMessages() );

    assertEquals( callCount.get(), 0 );
    assertNotNull( connector.getSchedulerLock() );

    safeAction( () -> {
      connector.setState( ConnectorState.ERROR );
      connector.setConnection( null );
    } );

    // The rest of the message has been skipped as no connection left
    assertFalse( connector.progressMessages() );

    assertNull( connector.getSchedulerLock() );
    assertEquals( callCount.get(), 1 );
  }

  @Test
  public void progressMessages_withError()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    connection.injectCurrentAreaOfInterestRequest( new AreaOfInterestRequest( new ChannelAddress( 0, 0 ),
                                                                              AreaOfInterestRequest.Type.REMOVE,
                                                                              null ) );
    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.resumeMessageScheduler();

    final boolean result2 = connector.progressMessages();

    assertFalse( result2 );

    assertNull( connector.getSchedulerLock() );

    handler.assertEventCountAtLeast( 1 );
    handler.assertNextEvent( MessageProcessFailureEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getError().getMessage(),
                    "Replicant-0046: Request to unsubscribe from channel at address 0.0 but not subscribed to channel." );
    } );
  }

  @Test
  public void requestSubscribe()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    connector.pauseMessageScheduler();

    final ChannelAddress address = new ChannelAddress( 1, 0 );

    assertFalse( connector.isAreaOfInterestRequestPending( AreaOfInterestRequest.Type.ADD, address, null ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.requestSubscribe( address, null );

    assertTrue( connector.isAreaOfInterestRequestPending( AreaOfInterestRequest.Type.ADD, address, null ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscribeRequestQueuedEvent.class, e -> assertEquals( e.getAddress(), address ) );
  }

  @Test
  public void requestSubscriptionUpdate()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.DYNAMIC,
                         ( f, e ) -> true,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1, ValueUtil.randomString(), new ChannelSchema[]{ channelSchema }, new EntitySchema[ 0 ] );
    final Connector connector = createConnector( schema );
    newConnection( connector );
    connector.pauseMessageScheduler();

    final ChannelAddress address = new ChannelAddress( 1, 0 );

    assertFalse( connector.isAreaOfInterestRequestPending( AreaOfInterestRequest.Type.UPDATE, address, null ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.requestSubscriptionUpdate( address, null );

    assertTrue( connector.isAreaOfInterestRequestPending( AreaOfInterestRequest.Type.UPDATE, address, null ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscriptionUpdateRequestQueuedEvent.class,
                             e -> assertEquals( e.getAddress(), address ) );
  }

  @Test
  public void requestSubscriptionUpdate_ChannelNot_DYNAMIC_Filter()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.STATIC,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1, ValueUtil.randomString(), new ChannelSchema[]{ channelSchema }, new EntitySchema[ 0 ] );
    final Connector connector = createConnector( schema );
    newConnection( connector );
    connector.pauseMessageScheduler();

    final ChannelAddress address = new ChannelAddress( 1, 0 );

    assertFalse( connector.isAreaOfInterestRequestPending( AreaOfInterestRequest.Type.UPDATE, address, null ) );

    final IllegalStateException exception =
      expectThrows( IllegalStateException.class, () -> connector.requestSubscriptionUpdate( address, null ) );

    assertEquals( exception.getMessage(),
                  "Replicant-0082: Connector.requestSubscriptionUpdate invoked for channel 1.0 but channel does not have a dynamic filter." );
  }

  @Test
  public void requestUnsubscribe()
    throws Exception
  {
    final Connector connector = createConnector();
    pauseScheduler();
    connector.pauseMessageScheduler();
    newConnection( connector );

    final ChannelAddress address = new ChannelAddress( 1, 0 );

    createSubscription( address, null, true );

    assertFalse( connector.isAreaOfInterestRequestPending( AreaOfInterestRequest.Type.REMOVE, address, null ) );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.requestUnsubscribe( address );

    Thread.sleep( 100 );

    assertTrue( connector.isAreaOfInterestRequestPending( AreaOfInterestRequest.Type.REMOVE, address, null ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( UnsubscribeRequestQueuedEvent.class,
                             e -> assertEquals( e.getAddress(), address ) );
  }

  @Test
  public void updateSubscriptionForFilteredEntities()
  {
    final SubscriptionUpdateEntityFilter<?> filter = ( f, entity ) -> entity.getId() > 0;
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.DYNAMIC,
                         filter,
                         true, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[ 0 ] );

    final Connector connector = createConnector( schema );
    newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final ChannelAddress address2 = new ChannelAddress( 1, 0, 2 );

    final Subscription subscription1 = createSubscription( address1, ValueUtil.randomString(), true );
    final Subscription subscription2 = createSubscription( address2, ValueUtil.randomString(), true );

    // Use Integer and String as arbitrary types for our entities...
    // Anything with id below 0 will be removed during update ...
    final Entity entity1 = findOrCreateEntity( Integer.class, -1 );
    final Entity entity2 = findOrCreateEntity( Integer.class, -2 );
    final Entity entity3 = findOrCreateEntity( Integer.class, -3 );
    final Entity entity4 = findOrCreateEntity( Integer.class, -4 );
    final Entity entity5 = findOrCreateEntity( String.class, 5 );
    final Entity entity6 = findOrCreateEntity( String.class, 6 );

    safeAction( () -> {
      entity1.linkToSubscription( subscription1 );
      entity2.linkToSubscription( subscription1 );
      entity3.linkToSubscription( subscription1 );
      entity4.linkToSubscription( subscription1 );
      entity5.linkToSubscription( subscription1 );
      entity6.linkToSubscription( subscription1 );

      entity3.linkToSubscription( subscription2 );
      entity4.linkToSubscription( subscription2 );

      assertEquals( subscription1.getEntities().size(), 2 );
      assertEquals( subscription1.findAllEntitiesByType( Integer.class ).size(), 4 );
      assertEquals( subscription1.findAllEntitiesByType( String.class ).size(), 2 );
      assertEquals( subscription2.getEntities().size(), 1 );
      assertEquals( subscription2.findAllEntitiesByType( Integer.class ).size(), 2 );
    } );

    safeAction( () -> connector.updateSubscriptionForFilteredEntities( subscription1 ) );

    safeAction( () -> {
      assertTrue( Disposable.isDisposed( entity1 ) );
      assertTrue( Disposable.isDisposed( entity2 ) );
      assertFalse( Disposable.isDisposed( entity3 ) );
      assertFalse( Disposable.isDisposed( entity4 ) );
      assertFalse( Disposable.isDisposed( entity5 ) );
      assertFalse( Disposable.isDisposed( entity6 ) );

      assertEquals( subscription1.getEntities().size(), 1 );
      assertEquals( subscription1.findAllEntitiesByType( Integer.class ).size(), 0 );
      assertEquals( subscription1.findAllEntitiesByType( String.class ).size(), 2 );
      assertEquals( subscription2.getEntities().size(), 1 );
      assertEquals( subscription2.findAllEntitiesByType( Integer.class ).size(), 2 );
    } );
  }

  @Test
  public void updateSubscriptionForFilteredEntities_badFilterType()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.STATIC,
                         null,
                         true, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[ 0 ] );

    final Connector connector = createConnector( schema );
    newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );

    final Subscription subscription1 = createSubscription( address1, ValueUtil.randomString(), true );

    final IllegalStateException exception =
      expectThrows( IllegalStateException.class,
                    () -> safeAction( () -> connector.updateSubscriptionForFilteredEntities( subscription1 ) ) );

    assertEquals( exception.getMessage(),
                  "Replicant-0079: Connector.updateSubscriptionForFilteredEntities invoked for address 1.0.1 but the channel does not have a DYNAMIC filter." );
  }

  @Test
  public void toAddress()
  {
    final Connector connector = createConnector();
    assertEquals( connector.toAddress( ChannelChange.create( 0, ChannelChange.Action.ADD, null ) ),
                  new ChannelAddress( 1, 0 ) );
    assertEquals( connector.toAddress( ChannelChange.create( 1, 2, ChannelChange.Action.ADD, null ) ),
                  new ChannelAddress( 1, 1, 2 ) );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void processEntityChanges()
  {
    final int schemaId = 1;
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.NONE,
                         null,
                         false, true,
                         Collections.emptyList() );
    final EntitySchema.Creator<Linkable> creator = mock( EntitySchema.Creator.class );
    final EntitySchema.Updater<Linkable> updater = mock( EntitySchema.Updater.class );
    final EntitySchema entitySchema =
      new EntitySchema( 0, ValueUtil.randomString(), Linkable.class, creator, updater );
    final SystemSchema schema =
      new SystemSchema( schemaId,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{ entitySchema } );
    final Connector connector = createConnector( schema );
    connector.setLinksToProcessPerTick( 1 );

    final Connection connection = newConnection( connector );
    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );

    final Linkable userObject1 = mock( Linkable.class );
    final Linkable userObject2 = mock( Linkable.class );

    // Pause scheduler to avoid converge of subscriptions
    pauseScheduler();

    final ChannelAddress address = new ChannelAddress( connector.getSchema().getId(), 1 );
    final Subscription subscription = createSubscription( address, null, true );

    // This entity is to be updated
    final Entity entity2 = findOrCreateEntity( Linkable.class, 2 );
    safeAction( () -> entity2.setUserObject( userObject2 ) );
    // It is already subscribed to channel and that should be fine
    safeAction( () -> entity2.linkToSubscription( subscription ) );
    // This entity is to be removed
    final Entity entity3 = findOrCreateEntity( Linkable.class, 3 );

    final EntityChangeData data1 = mock( EntityChangeData.class );
    final EntityChangeData data2 = mock( EntityChangeData.class );
    final EntityChange[] entityChanges = {
      // Update changes
      EntityChange.create( 1, 0, new String[]{ "1" }, data1 ),
      EntityChange.create( 2, 0, new String[]{ "1" }, data2 ),
      // Remove change
      EntityChange.create( 3, 0, new String[]{ "1" } )
    };
    final ChangeSet changeSet = ChangeSet.create( ValueUtil.randomInt(), null, entityChanges );
    response.recordChangeSet( changeSet, null );

    when( creator.createEntity( 1, data1 ) ).thenReturn( userObject1 );

    assertEquals( response.getUpdatedEntities().size(), 0 );
    assertEquals( response.getEntityUpdateCount(), 0 );
    assertEquals( response.getEntityRemoveCount(), 0 );

    connector.setChangesToProcessPerTick( 1 );

    connector.processEntityChanges();

    verify( creator, times( 1 ) ).createEntity( 1, data1 );
    verify( updater, never() ).updateEntity( userObject1, data1 );
    verify( creator, never() ).createEntity( 2, data2 );
    verify( updater, never() ).updateEntity( userObject2, data2 );

    assertEquals( response.getUpdatedEntities().size(), 1 );
    assertTrue( response.getUpdatedEntities().contains( userObject1 ) );
    assertEquals( response.getEntityUpdateCount(), 1 );
    assertEquals( response.getEntityRemoveCount(), 0 );

    connector.setChangesToProcessPerTick( 2 );

    connector.processEntityChanges();

    verify( creator, times( 1 ) ).createEntity( 1, data1 );
    verify( updater, never() ).updateEntity( userObject1, data1 );
    verify( creator, never() ).createEntity( 2, data2 );
    verify( updater, times( 1 ) ).updateEntity( userObject2, data2 );

    assertEquals( response.getUpdatedEntities().size(), 2 );
    assertTrue( response.getUpdatedEntities().contains( userObject1 ) );
    assertTrue( response.getUpdatedEntities().contains( userObject2 ) );
    assertEquals( response.getEntityUpdateCount(), 2 );
    assertEquals( response.getEntityRemoveCount(), 1 );
    assertFalse( Disposable.isDisposed( entity2 ) );
    assertTrue( Disposable.isDisposed( entity3 ) );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void processEntityChanges_referenceNonExistentSubscription()
  {
    final int schemaId = 1;
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.NONE,
                         null,
                         false, true,
                         Collections.emptyList() );
    final EntitySchema.Creator<Linkable> creator = mock( EntitySchema.Creator.class );
    final EntitySchema.Updater<Linkable> updater = mock( EntitySchema.Updater.class );
    final EntitySchema entitySchema =
      new EntitySchema( 0, ValueUtil.randomString(), Linkable.class, creator, updater );
    final SystemSchema schema =
      new SystemSchema( schemaId,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{ entitySchema } );
    final Connector connector = createConnector( schema );
    connector.setLinksToProcessPerTick( 1 );

    final Connection connection = newConnection( connector );
    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );

    final Linkable userObject1 = mock( Linkable.class );

    // Pause scheduler to avoid converge of subscriptions
    pauseScheduler();

    final EntityChangeData data1 = mock( EntityChangeData.class );
    final EntityChange[] entityChanges = {
      EntityChange.create( 1, 0, new String[]{ "1" }, data1 )
    };
    final ChangeSet changeSet = ChangeSet.create( 42, null, entityChanges );
    response.recordChangeSet( changeSet, null );

    when( creator.createEntity( 1, data1 ) ).thenReturn( userObject1 );

    final IllegalStateException exception =
      expectThrows( IllegalStateException.class, connector::processEntityChanges );
    assertEquals( exception.getMessage(),
                  "Replicant-0069: ChangeSet 42 contained an EntityChange message referencing channel 1.1 but no such subscription exists locally." );
  }

  @Test
  public void processEntityChanges_deleteNonExistingEntity()
  {
    final int schemaId = 1;
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.NONE,
                         null,
                         false, true,
                         Collections.emptyList() );
    final EntitySchema entitySchema =
      new EntitySchema( 0, ValueUtil.randomString(), MyEntity.class, ( i, d ) -> new MyEntity(), null );
    final SystemSchema schema =
      new SystemSchema( schemaId,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{ entitySchema } );
    final Connector connector = createConnector( schema );
    connector.setLinksToProcessPerTick( 1 );

    final Connection connection = newConnection( connector );
    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );

    // Pause scheduler to avoid converge of subscriptions
    pauseScheduler();

    final EntityChange[] entityChanges = {
      // Remove change
      EntityChange.create( 3, 0, new String[]{ "1" } )
    };
    final ChangeSet changeSet = ChangeSet.create( 23, null, entityChanges );
    response.recordChangeSet( changeSet, null );

    connector.setChangesToProcessPerTick( 1 );

    connector.processEntityChanges();

    assertEquals( response.getEntityRemoveCount(), 0 );
  }

  @Test
  public void processEntityLinks()
  {
    final Connector connector = createConnector();
    connector.setLinksToProcessPerTick( 1 );

    final Connection connection = newConnection( connector );
    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );

    final Linkable entity1 = mock( Linkable.class );
    final Linkable entity2 = mock( Linkable.class );
    final Linkable entity3 = mock( Linkable.class );
    final Linkable entity4 = mock( Linkable.class );

    response.changeProcessed( entity1 );
    response.changeProcessed( entity2 );
    response.changeProcessed( entity3 );
    response.changeProcessed( entity4 );

    verify( entity1, never() ).link();
    verify( entity2, never() ).link();
    verify( entity3, never() ).link();
    verify( entity4, never() ).link();

    assertEquals( response.getEntityLinkCount(), 0 );

    connector.setLinksToProcessPerTick( 1 );

    connector.processEntityLinks();

    assertEquals( response.getEntityLinkCount(), 1 );
    verify( entity1, times( 1 ) ).link();
    verify( entity2, never() ).link();
    verify( entity3, never() ).link();
    verify( entity4, never() ).link();

    connector.setLinksToProcessPerTick( 2 );

    connector.processEntityLinks();

    assertEquals( response.getEntityLinkCount(), 3 );
    verify( entity1, times( 1 ) ).link();
    verify( entity2, times( 1 ) ).link();
    verify( entity3, times( 1 ) ).link();
    verify( entity4, never() ).link();
  }

  @Test
  public void completeAreaOfInterestRequest()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    connection.injectCurrentAreaOfInterestRequest( new AreaOfInterestRequest( new ChannelAddress( 1, 0 ),
                                                                              AreaOfInterestRequest.Type.ADD,
                                                                              null ) );

    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.completeAreaOfInterestRequest();

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
  }

  @Test
  public void processChannelChanges_add()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );

    final int channelId = 0;
    final int subChannelId = ValueUtil.randomInt();
    final String filter = ValueUtil.randomString();
    final ChannelChange[] channelChanges =
      new ChannelChange[]{ ChannelChange.create( channelId, subChannelId, ChannelChange.Action.ADD, filter ) };
    response.recordChangeSet( ChangeSet.create( ValueUtil.randomInt(), channelChanges, null ), null );

    assertTrue( response.needsChannelChangesProcessed() );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.processChannelChanges();

    assertFalse( response.needsChannelChangesProcessed() );

    final ChannelAddress address = new ChannelAddress( 1, channelId, subChannelId );
    final Subscription subscription =
      safeAction( () -> Replicant.context().findSubscription( address ) );
    assertNotNull( subscription );
    assertEquals( subscription.getAddress(), address );
    safeAction( () -> assertEquals( subscription.getFilter(), filter ) );
    safeAction( () -> assertFalse( subscription.isExplicitSubscription() ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscriptionCreatedEvent.class, e -> {
      assertEquals( e.getSubscription().getAddress(), address );
      safeAction( () -> assertEquals( e.getSubscription().getFilter(), filter ) );
    } );
  }

  @Test
  public void processChannelChanges_add_withCorrespondingAreaOfInterest()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );

    final int channelId = 0;
    final int subChannelId = ValueUtil.randomInt();
    final String filter = ValueUtil.randomString();

    final ChannelAddress address = new ChannelAddress( 1, channelId, subChannelId );

    safeAction( () -> Replicant.context().createOrUpdateAreaOfInterest( address, filter ) );

    final ChannelChange[] channelChanges =
      new ChannelChange[]{ ChannelChange.create( channelId, subChannelId, ChannelChange.Action.ADD, filter ) };
    response.recordChangeSet( ChangeSet.create( ValueUtil.randomInt(), channelChanges, null ), null );

    assertTrue( response.needsChannelChangesProcessed() );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.processChannelChanges();

    assertFalse( response.needsChannelChangesProcessed() );

    final Subscription subscription =
      safeAction( () -> Replicant.context().findSubscription( address ) );
    assertNotNull( subscription );
    assertEquals( subscription.getAddress(), address );
    safeAction( () -> assertEquals( subscription.getFilter(), filter ) );
    safeAction( () -> assertTrue( subscription.isExplicitSubscription() ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscriptionCreatedEvent.class, e -> {
      assertEquals( e.getSubscription().getAddress(), address );
      safeAction( () -> assertEquals( e.getSubscription().getFilter(), filter ) );
    } );
  }

  @Test
  public void processChannelChanges_addConvertingImplicitToExplicit()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    connector.pauseMessageScheduler();

    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );

    final ChannelAddress address = new ChannelAddress( 1, 0, ValueUtil.randomInt() );
    final int channelId = address.getChannelId();
    final int subChannelId = Objects.requireNonNull( address.getId() );
    final String filter = ValueUtil.randomString();

    safeAction( () -> Replicant.context().createOrUpdateAreaOfInterest( address, filter ) );

    final ChannelChange[] channelChanges =
      new ChannelChange[]{ ChannelChange.create( channelId, subChannelId, ChannelChange.Action.ADD, filter ) };
    response.recordChangeSet( ChangeSet.create( ValueUtil.randomInt(), channelChanges, null ), null );

    final AreaOfInterestRequest request = new AreaOfInterestRequest( address, AreaOfInterestRequest.Type.ADD, filter );
    connection.injectCurrentAreaOfInterestRequest( request );
    request.markAsInProgress();

    assertTrue( response.needsChannelChangesProcessed() );
    assertEquals( response.getChannelAddCount(), 0 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.processChannelChanges();

    assertFalse( response.needsChannelChangesProcessed() );
    assertEquals( response.getChannelAddCount(), 1 );

    final Subscription subscription =
      safeAction( () -> Replicant.context().findSubscription( address ) );
    assertNotNull( subscription );
    assertEquals( subscription.getAddress(), address );
    safeAction( () -> assertEquals( subscription.getFilter(), filter ) );
    safeAction( () -> assertTrue( subscription.isExplicitSubscription() ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscriptionCreatedEvent.class, e -> {
      assertEquals( e.getSubscription().getAddress(), address );
      safeAction( () -> assertEquals( e.getSubscription().getFilter(), filter ) );
    } );
  }

  @Test
  public void processChannelChanges_remove()
  {
    final Connector connector = createConnector();
    connector.pauseMessageScheduler();

    final Connection connection = newConnection( connector );

    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );

    final ChannelAddress address = new ChannelAddress( 1, 0, ValueUtil.randomInt() );
    final int channelId = address.getChannelId();
    final int subChannelId = Objects.requireNonNull( address.getId() );

    final String filter = ValueUtil.randomString();
    final ChannelChange[] channelChanges =
      new ChannelChange[]{ ChannelChange.create( channelId, subChannelId, ChannelChange.Action.REMOVE, null ) };
    response.recordChangeSet( ChangeSet.create( ValueUtil.randomInt(), channelChanges, null ), null );

    final Subscription initialSubscription = createSubscription( address, filter, true );

    assertTrue( response.needsChannelChangesProcessed() );
    assertEquals( response.getChannelRemoveCount(), 0 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.processChannelChanges();

    assertFalse( response.needsChannelChangesProcessed() );
    assertEquals( response.getChannelRemoveCount(), 1 );

    final Subscription subscription =
      safeAction( () -> Replicant.context().findSubscription( address ) );
    assertNull( subscription );
    assertTrue( Disposable.isDisposed( initialSubscription ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscriptionDisposedEvent.class,
                             e -> assertEquals( e.getSubscription().getAddress(), address ) );
  }

  @Test
  public void processChannelChanges_remove_withAreaOfInterest()
  {
    final Connector connector = createConnector();
    connector.pauseMessageScheduler();

    final Connection connection = newConnection( connector );

    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );

    final ChannelAddress address = new ChannelAddress( 1, 0, ValueUtil.randomInt() );
    final int channelId = address.getChannelId();
    final int subChannelId = Objects.requireNonNull( address.getId() );

    final AreaOfInterest areaOfInterest =
      safeAction( () -> Replicant.context().createOrUpdateAreaOfInterest( address, null ) );

    final String filter = ValueUtil.randomString();
    final ChannelChange[] channelChanges =
      new ChannelChange[]{ ChannelChange.create( channelId, subChannelId, ChannelChange.Action.REMOVE, null ) };
    response.recordChangeSet( ChangeSet.create( ValueUtil.randomInt(), channelChanges, null ), null );

    final Subscription initialSubscription = createSubscription( address, filter, true );

    assertTrue( response.needsChannelChangesProcessed() );
    assertEquals( response.getChannelRemoveCount(), 0 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.processChannelChanges();

    assertFalse( response.needsChannelChangesProcessed() );
    assertEquals( response.getChannelRemoveCount(), 1 );

    final Subscription subscription =
      safeAction( () -> Replicant.context().findSubscription( address ) );
    assertNull( subscription );
    assertTrue( Disposable.isDisposed( initialSubscription ) );

    assertTrue( Disposable.isDisposed( areaOfInterest ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( AreaOfInterestDisposedEvent.class,
                             e -> assertEquals( e.getAreaOfInterest().getAddress(), address ) );
    handler.assertNextEvent( SubscriptionDisposedEvent.class,
                             e -> assertEquals( e.getSubscription().getAddress(), address ) );
  }

  @Test
  public void processChannelChanges_remove_WithMissingSubscription()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );

    final ChannelAddress address = new ChannelAddress( 1, 0, 72 );
    final int channelId = address.getChannelId();
    final int subChannelId = Objects.requireNonNull( address.getId() );

    final ChannelChange[] channelChanges =
      new ChannelChange[]{ ChannelChange.create( channelId, subChannelId, ChannelChange.Action.REMOVE, null ) };
    response.recordChangeSet( ChangeSet.create( ValueUtil.randomInt(), channelChanges, null ), null );

    assertTrue( response.needsChannelChangesProcessed() );
    assertEquals( response.getChannelRemoveCount(), 0 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.processChannelChanges();

    assertFalse( response.needsChannelChangesProcessed() );
    assertEquals( response.getChannelRemoveCount(), 0 );

    handler.assertEventCount( 0 );
  }

  @Test
  public void processChannelChanges_update()
  {
    final SubscriptionUpdateEntityFilter<?> filter = mock( SubscriptionUpdateEntityFilter.class );
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.DYNAMIC,
                         filter,
                         true, true,
                         Collections.emptyList() );
    final EntitySchema entitySchema =
      new EntitySchema( 0, ValueUtil.randomString(), String.class, ( i, d ) -> "", null );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{ entitySchema } );

    final Connector connector = createConnector( schema );
    connector.pauseMessageScheduler();

    final Connection connection = newConnection( connector );

    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );

    final ChannelAddress address = new ChannelAddress( 1, 0, ValueUtil.randomInt() );
    final int channelId = address.getChannelId();
    final int subChannelId = Objects.requireNonNull( address.getId() );

    final String oldFilter = ValueUtil.randomString();
    final String newFilter = ValueUtil.randomString();
    final ChannelChange[] channelChanges =
      new ChannelChange[]{ ChannelChange.create( channelId, subChannelId, ChannelChange.Action.UPDATE, newFilter ) };
    response.recordChangeSet( ChangeSet.create( ValueUtil.randomInt(), channelChanges, null ), null );

    final Subscription initialSubscription = createSubscription( address, oldFilter, true );

    assertTrue( response.needsChannelChangesProcessed() );
    assertEquals( response.getChannelUpdateCount(), 0 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.processChannelChanges();

    assertFalse( response.needsChannelChangesProcessed() );
    assertEquals( response.getChannelUpdateCount(), 1 );

    final Subscription subscription =
      safeAction( () -> Replicant.context().findSubscription( address ) );
    assertNotNull( subscription );
    assertFalse( Disposable.isDisposed( initialSubscription ) );

    handler.assertEventCount( 0 );
  }

  @Test
  public void processChannelChanges_update_forNonDYNAMICChannel()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.NONE,
                         null,
                         true,
                         true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1, ValueUtil.randomString(), new ChannelSchema[]{ channelSchema }, new EntitySchema[ 0 ] );
    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );

    final ChannelAddress address = new ChannelAddress( 1, 0, 2223 );
    final int channelId = address.getChannelId();
    final int subChannelId = Objects.requireNonNull( address.getId() );

    final String oldFilter = ValueUtil.randomString();
    final String newFilter = ValueUtil.randomString();
    final ChannelChange[] channelChanges =
      new ChannelChange[]{ ChannelChange.create( channelId, subChannelId, ChannelChange.Action.UPDATE, newFilter ) };
    response.recordChangeSet( ChangeSet.create( ValueUtil.randomInt(), channelChanges, null ), null );

    createSubscription( address, oldFilter, true );

    final IllegalStateException exception =
      expectThrows( IllegalStateException.class, connector::processChannelChanges );

    assertEquals( exception.getMessage(),
                  "Replicant-0078: Received ChannelChange of type UPDATE for address 1.0.2223 but the channel does not have a DYNAMIC filter." );
  }

  @Test
  public void processChannelChanges_update_missingSubscription()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connection.setCurrentMessageResponse( response );

    final ChannelAddress address = new ChannelAddress( 1, 0, 42 );
    final int channelId = address.getChannelId();
    final int subChannelId = Objects.requireNonNull( address.getId() );

    final String newFilter = ValueUtil.randomString();
    final ChannelChange[] channelChanges =
      new ChannelChange[]{ ChannelChange.create( channelId, subChannelId, ChannelChange.Action.UPDATE, newFilter ) };
    response.recordChangeSet( ChangeSet.create( ValueUtil.randomInt(), channelChanges, null ), null );

    assertTrue( response.needsChannelChangesProcessed() );
    assertEquals( response.getChannelUpdateCount(), 0 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final IllegalStateException exception =
      expectThrows( IllegalStateException.class, connector::processChannelChanges );

    assertTrue( response.needsChannelChangesProcessed() );
    assertEquals( response.getChannelUpdateCount(), 0 );

    assertEquals( exception.getMessage(),
                  "Replicant-0033: Received ChannelChange of type UPDATE for address 1.0.42 but no such subscription exists." );

    handler.assertEventCount( 0 );
  }

  @Test
  public void removeExplicitSubscriptions()
  {
    // Pause converger
    pauseScheduler();

    final Connector connector = createConnector();

    final ChannelAddress address1 = new ChannelAddress( 1, 1, 1 );
    final ChannelAddress address2 = new ChannelAddress( 1, 1, 2 );
    final ChannelAddress address3 = new ChannelAddress( 1, 1, 3 );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    requests.add( new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.REMOVE, null ) );
    requests.add( new AreaOfInterestRequest( address2, AreaOfInterestRequest.Type.REMOVE, null ) );
    requests.add( new AreaOfInterestRequest( address3, AreaOfInterestRequest.Type.REMOVE, null ) );

    final Subscription subscription1 = createSubscription( address1, null, true );
    // Address2 is already implicit ...
    createSubscription( address2, null, false );
    // Address3 has no subscription ... maybe not converged yet

    connector.removeExplicitSubscriptions( requests );

    safeAction( () -> assertFalse( subscription1.isExplicitSubscription() ) );
  }

  @Test
  public void removeExplicitSubscriptions_passedBadAction()
  {
    // Pause converger
    pauseScheduler();

    final Connector connector = createConnector();

    final ChannelAddress address1 = new ChannelAddress( 1, 1, 1 );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    requests.add( new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.ADD, null ) );

    createSubscription( address1, null, true );

    final IllegalStateException exception =
      expectThrows( IllegalStateException.class,
                    () -> safeAction( () -> connector.removeExplicitSubscriptions( requests ) ) );
    assertEquals( exception.getMessage(),
                  "Replicant-0034: Connector.removeExplicitSubscriptions() invoked with request with type that is not REMOVE. Request: AreaOfInterestRequest[Type=ADD Address=1.1.1]" );
  }

  @Test
  public void removeUnneededRemoveRequests_whenInvariantsDisabled()
  {
    final Connector connector = createConnector();

    final ChannelAddress address1 = new ChannelAddress( 1, 1, 1 );
    final ChannelAddress address2 = new ChannelAddress( 1, 1, 2 );
    final ChannelAddress address3 = new ChannelAddress( 1, 1, 3 );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.REMOVE, null );
    final AreaOfInterestRequest request2 =
      new AreaOfInterestRequest( address2, AreaOfInterestRequest.Type.REMOVE, null );
    final AreaOfInterestRequest request3 =
      new AreaOfInterestRequest( address3, AreaOfInterestRequest.Type.REMOVE, null );
    requests.add( request1 );
    requests.add( request2 );
    requests.add( request3 );

    requests.forEach( AreaOfInterestRequest::markAsInProgress );

    createSubscription( address1, null, true );
    // Address2 is already implicit ...
    createSubscription( address2, null, false );
    // Address3 has no subscription ... maybe not converged yet

    ReplicantTestUtil.noCheckInvariants();

    connector.removeUnneededRemoveRequests( requests );

    assertEquals( requests.size(), 1 );
    assertTrue( requests.contains( request1 ) );
    assertTrue( request1.isInProgress() );
    assertFalse( request2.isInProgress() );
    assertFalse( request3.isInProgress() );
  }

  @Test
  public void removeUnneededRemoveRequests_noSubscription()
  {
    final Connector connector = createConnector();

    final ChannelAddress address1 = new ChannelAddress( 1, 1, 1 );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.REMOVE, null );
    requests.add( request1 );

    requests.forEach( AreaOfInterestRequest::markAsInProgress );

    final IllegalStateException exception =
      expectThrows( IllegalStateException.class,
                    () -> safeAction( () -> connector.removeUnneededRemoveRequests( requests ) ) );

    assertEquals( exception.getMessage(),
                  "Replicant-0046: Request to unsubscribe from channel at address 1.1.1 but not subscribed to channel." );
  }

  @Test
  public void removeUnneededRemoveRequests_implicitSubscription()
  {
    final Connector connector = createConnector();

    final ChannelAddress address1 = new ChannelAddress( 1, 1, 1 );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.REMOVE, null );
    requests.add( request1 );

    requests.forEach( AreaOfInterestRequest::markAsInProgress );

    createSubscription( address1, null, false );

    final IllegalStateException exception =
      expectThrows( IllegalStateException.class,
                    () -> safeAction( () -> connector.removeUnneededRemoveRequests( requests ) ) );

    assertEquals( exception.getMessage(),
                  "Replicant-0047: Request to unsubscribe from channel at address 1.1.1 but subscription is not an explicit subscription." );
  }

  @Test
  public void removeUnneededUpdateRequests_whenInvariantsDisabled()
  {
    final Connector connector = createConnector();

    final ChannelAddress address1 = new ChannelAddress( 1, 1, 1 );
    final ChannelAddress address2 = new ChannelAddress( 1, 1, 2 );
    final ChannelAddress address3 = new ChannelAddress( 1, 1, 3 );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.UPDATE, null );
    final AreaOfInterestRequest request2 =
      new AreaOfInterestRequest( address2, AreaOfInterestRequest.Type.UPDATE, null );
    final AreaOfInterestRequest request3 =
      new AreaOfInterestRequest( address3, AreaOfInterestRequest.Type.UPDATE, null );
    requests.add( request1 );
    requests.add( request2 );
    requests.add( request3 );

    requests.forEach( AreaOfInterestRequest::markAsInProgress );

    createSubscription( address1, null, true );
    // Address2 is already implicit ...
    createSubscription( address2, null, false );
    // Address3 has no subscription ... maybe not converged yet

    ReplicantTestUtil.noCheckInvariants();

    connector.removeUnneededUpdateRequests( requests );

    assertEquals( requests.size(), 2 );
    assertTrue( requests.contains( request1 ) );
    assertTrue( requests.contains( request2 ) );
    assertTrue( request1.isInProgress() );
    assertTrue( request2.isInProgress() );
    assertFalse( request3.isInProgress() );
  }

  @Test
  public void removeUnneededUpdateRequests_noSubscription()
  {
    final Connector connector = createConnector();

    final ChannelAddress address1 = new ChannelAddress( 1, 1, 1 );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.UPDATE, null );
    requests.add( request1 );

    requests.forEach( AreaOfInterestRequest::markAsInProgress );

    final IllegalStateException exception =
      expectThrows( IllegalStateException.class,
                    () -> safeAction( () -> connector.removeUnneededUpdateRequests( requests ) ) );

    assertEquals( exception.getMessage(),
                  "Replicant-0048: Request to update channel at address 1.1.1 but not subscribed to channel." );
  }

  @Test
  public void validateWorld_invalidEntity()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connector.ensureConnection().setCurrentMessageResponse( response );

    assertFalse( response.hasWorldBeenValidated() );

    final EntityService entityService = Replicant.context().getEntityService();
    final Entity entity1 =
      safeAction( () -> entityService.findOrCreateEntity( "MyEntity/1", MyEntity.class, 1 ) );
    final Exception error = new Exception();
    safeAction( () -> entity1.setUserObject( new MyEntity( error ) ) );

    final IllegalStateException exception =
      expectThrows( IllegalStateException.class, connector::validateWorld );

    assertEquals( exception.getMessage(),
                  "Replicant-0065: Entity failed to verify during validation process. Entity = MyEntity/1" );

    assertTrue( response.hasWorldBeenValidated() );
  }

  @Test
  public void validateWorld_invalidEntity_ignoredIfCOmpileSettingDisablesValidation()
  {
    ReplicantTestUtil.noValidateEntitiesOnLoad();
    final Connector connector = createConnector();
    newConnection( connector );
    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connector.ensureConnection().setCurrentMessageResponse( response );

    assertTrue( response.hasWorldBeenValidated() );

    final EntityService entityService = Replicant.context().getEntityService();
    final Entity entity1 =
      safeAction( () -> entityService.findOrCreateEntity( "MyEntity/1", MyEntity.class, 1 ) );
    final Exception error = new Exception();
    safeAction( () -> entity1.setUserObject( new MyEntity( error ) ) );

    connector.validateWorld();

    assertTrue( response.hasWorldBeenValidated() );
  }

  @Test
  public void validateWorld_validEntity()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    final MessageResponse response = new MessageResponse( ValueUtil.randomString() );
    connector.ensureConnection().setCurrentMessageResponse( response );

    assertFalse( response.hasWorldBeenValidated() );

    final EntityService entityService = Replicant.context().getEntityService();
    safeAction( () -> entityService.findOrCreateEntity( "MyEntity/1", MyEntity.class, 1 ) );

    connector.validateWorld();

    assertTrue( response.hasWorldBeenValidated() );
  }

  static class MyEntity
    implements Verifiable
  {
    @Nullable
    private final Exception _exception;

    MyEntity()
    {
      this( null );
    }

    MyEntity( @Nullable final Exception exception )
    {
      _exception = exception;
    }

    @Override
    public void verify()
      throws Exception
    {
      if ( null != _exception )
      {
        throw _exception;
      }
    }
  }

  @Test
  public void parseMessageResponse_basicMessage()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    @Language( "json" )
    final String rawJsonData = "{\"last_id\": 1}";
    final MessageResponse response = new MessageResponse( rawJsonData );

    connection.setCurrentMessageResponse( response );
    assertEquals( connection.getPendingResponses().size(), 0 );

    connector.parseMessageResponse();

    assertNull( response.getRawJsonData() );
    final ChangeSet changeSet = response.getChangeSet();
    assertNotNull( changeSet );
    assertEquals( changeSet.getSequence(), 1 );
    assertNull( response.getRequest() );

    assertEquals( connection.getPendingResponses().size(), 1 );
    assertNull( connection.getCurrentMessageResponse() );
  }

  @Test
  public void parseMessageResponse_requestPresent()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    final Request request = connection.newRequest( "SomeAction" );

    final int requestId = request.getRequestId();

    @Language( "json" )
    final String rawJsonData = "{\"last_id\": 1, \"requestId\": " + requestId + "}";
    final MessageResponse response = new MessageResponse( rawJsonData );

    connection.setCurrentMessageResponse( response );
    assertEquals( connection.getPendingResponses().size(), 0 );

    connector.parseMessageResponse();

    assertNull( response.getRawJsonData() );
    final ChangeSet changeSet = response.getChangeSet();
    assertNotNull( changeSet );
    assertEquals( changeSet.getSequence(), 1 );
    assertEquals( response.getRequest(), request.getEntry() );

    assertEquals( connection.getPendingResponses().size(), 1 );
    assertNull( connection.getCurrentMessageResponse() );
  }

  @Test
  public void parseMessageResponse_cacheResult()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.NONE,
                         null,
                         true,
                         true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1, ValueUtil.randomString(), new ChannelSchema[]{ channelSchema }, new EntitySchema[ 0 ] );
    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final Request request = connection.newRequest( "SomeAction" );

    final int requestId = request.getRequestId();

    final String etag = ValueUtil.randomString();

    final String rawJsonData =
      "{\"last_id\": 1" +
      ", \"requestId\": " + requestId +
      ", \"etag\": \"" + etag + "\"" +
      ", \"channel_actions\": [ { \"cid\": 0, \"action\": \"add\"} ] }";

    final MessageResponse response = new MessageResponse( rawJsonData );

    connection.setCurrentMessageResponse( response );
    assertEquals( connection.getPendingResponses().size(), 0 );

    final CacheService cacheService = new TestCacheService();
    Replicant.context().setCacheService( cacheService );
    assertNull( cacheService.lookup( ValueUtil.randomString() ) );

    connector.parseMessageResponse();

    assertNull( response.getRawJsonData() );
    final ChangeSet changeSet = response.getChangeSet();
    assertNotNull( changeSet );
    assertEquals( changeSet.getSequence(), 1 );
    assertEquals( response.getRequest(), request.getEntry() );

    assertEquals( connection.getPendingResponses().size(), 1 );
    assertNull( connection.getCurrentMessageResponse() );

    final String cacheKey = "RC-1.0";
    final CacheEntry entry = cacheService.lookup( cacheKey );
    assertNotNull( entry );
    assertEquals( entry.getKey(), cacheKey );
    assertEquals( entry.getETag(), etag );
    assertEquals( entry.getContent(), rawJsonData );
  }

  @Test
  public void parseMessageResponse_eTagWhenNotCacheCandidate()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    final Request request = connection.newRequest( "SomeAction" );

    final int requestId = request.getRequestId();

    final String etag = ValueUtil.randomString();

    final String rawJsonData =
      "{\"last_id\": 1" +
      ", \"requestId\": " + requestId +
      ", \"etag\": \"" + etag + "\"" +
      ", \"channel_actions\": [ { \"cid\": 0, \"action\": \"add\"}, { \"cid\": 1, \"scid\": 1, \"action\": \"add\"} ] }";

    final MessageResponse response = new MessageResponse( rawJsonData );

    connection.setCurrentMessageResponse( response );
    assertEquals( connection.getPendingResponses().size(), 0 );

    final CacheService cacheService = new TestCacheService();
    Replicant.context().setCacheService( cacheService );
    assertNull( cacheService.lookup( ValueUtil.randomString() ) );

    final IllegalStateException exception =
      expectThrows( IllegalStateException.class, connector::parseMessageResponse );

    assertEquals( exception.getMessage(),
                  "Replicant-0072: eTag in reply for ChangeSet 1 but ChangeSet is not a candidate for caching." );
  }

  @Test
  public void parseMessageResponse_oob()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    final Request request = connection.newRequest( "SomeAction" );

    final int requestId = request.getRequestId();

    @Language( "json" )
    final String rawJsonData = "{\"last_id\": 1, \"requestId\": " + requestId + "}";
    final SafeProcedure oobCompletionAction = () -> {
    };
    final MessageResponse response = new MessageResponse( rawJsonData, oobCompletionAction );

    connection.setCurrentMessageResponse( response );
    assertEquals( connection.getPendingResponses().size(), 0 );

    connector.parseMessageResponse();

    assertNull( response.getRawJsonData() );
    final ChangeSet changeSet = response.getChangeSet();
    assertNotNull( changeSet );
    assertEquals( changeSet.getSequence(), 1 );
    assertNull( response.getRequest() );

    assertEquals( connection.getPendingResponses().size(), 1 );
    assertNull( connection.getCurrentMessageResponse() );
  }

  @Test
  public void parseMessageResponse_invalidRequestId()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    @Language( "json" )
    final String rawJsonData = "{\"last_id\": 1, \"requestId\": 22}";
    final MessageResponse response = new MessageResponse( rawJsonData );

    connection.setCurrentMessageResponse( response );
    assertEquals( connection.getPendingResponses().size(), 0 );

    final IllegalStateException exception =
      expectThrows( IllegalStateException.class, connector::parseMessageResponse );

    assertEquals( exception.getMessage(),
                  "Replicant-0066: Unable to locate request with id '22' specified for ChangeSet with sequence 1. Existing Requests: {}" );
  }

  @Test
  public void completeMessageResponse()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    final MessageResponse response = new MessageResponse( "" );

    final ChangeSet changeSet = ChangeSet.create( 23, null, null );
    response.recordChangeSet( changeSet, null );

    connection.setLastRxSequence( 22 );
    connection.setCurrentMessageResponse( response );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.completeMessageResponse();

    assertEquals( connection.getLastRxSequence(), 23 );
    assertNull( connection.getCurrentMessageResponse() );
    safeAction( () -> assertTrue( connector.isPendingResponseQueueEmpty() ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( MessageProcessedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getSchemaName(), connector.getSchema().getName() );
      assertEquals( e.getDataLoadStatus().getSequence(), changeSet.getSequence() );
    } );
  }

  @Test
  public void completeMessageResponse_hasContent()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );
    safeAction( () -> connector.setLastTxRequestId( 2 ) );
    safeAction( () -> connector.setLastRxRequestId( 2 ) );

    final MessageResponse response = new MessageResponse( "" );

    final ChangeSet changeSet =
      ChangeSet.create( 23, new ChannelChange[]{ ChannelChange.create( 1, ChannelChange.Action.ADD, null ) }, null );
    response.recordChangeSet( changeSet, null );

    connection.setLastRxSequence( 22 );
    connection.setCurrentMessageResponse( response );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    connector.completeMessageResponse();

    assertEquals( connection.getLastRxSequence(), 23 );
    assertNull( connection.getCurrentMessageResponse() );
    safeAction( () -> assertTrue( connector.isPendingResponseQueueEmpty() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( MessageProcessedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getSchemaName(), connector.getSchema().getName() );
      assertEquals( e.getDataLoadStatus().getSequence(), changeSet.getSequence() );
    } );
    handler.assertNextEvent( SyncRequestEvent.class,
                             e -> assertEquals( e.getSchemaId(), connector.getSchema().getId() ) );
  }

  @Test
  public void completeMessageResponse_stillMessagesPending()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    final MessageResponse response = new MessageResponse( "" );

    response.recordChangeSet( ChangeSet.create( 23, null, null ), null );

    connection.setLastRxSequence( 22 );
    connection.setCurrentMessageResponse( response );

    connection.enqueueResponse( ValueUtil.randomString() );

    connector.completeMessageResponse();

    safeAction( () -> assertFalse( connector.isPendingResponseQueueEmpty() ) );
  }

  @Test
  public void completeMessageResponse_withPostAction()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    final MessageResponse response = new MessageResponse( "" );

    final ChangeSet changeSet = ChangeSet.create( 23, null, null );
    response.recordChangeSet( changeSet, null );

    connection.setLastRxSequence( 22 );
    connection.setCurrentMessageResponse( response );

    final AtomicInteger postActionCallCount = new AtomicInteger();
    connector.setPostMessageResponseAction( postActionCallCount::incrementAndGet );

    assertEquals( postActionCallCount.get(), 0 );

    connector.completeMessageResponse();

    assertEquals( postActionCallCount.get(), 1 );
  }

  @Test
  public void completeMessageResponse_OOBMessage()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    final AtomicInteger completionCallCount = new AtomicInteger();
    final MessageResponse response = new MessageResponse( "", completionCallCount::incrementAndGet );

    final ChangeSet changeSet = ChangeSet.create( 23, 1234, null, null, null );
    response.recordChangeSet( changeSet, null );

    connection.setLastRxSequence( 22 );
    connection.setCurrentMessageResponse( response );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    assertEquals( completionCallCount.get(), 0 );

    connector.completeMessageResponse();

    assertEquals( completionCallCount.get(), 1 );
    assertEquals( connection.getLastRxSequence(), 22 );
    assertNull( connection.getCurrentMessageResponse() );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( MessageProcessedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getSchemaName(), connector.getSchema().getName() );
      assertEquals( e.getDataLoadStatus().getSequence(), changeSet.getSequence() );
    } );
  }

  @Test
  public void completeMessageResponse_MessageWithRequest_RPCComplete()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    final Request request = connection.newRequest( "SomeAction" );

    final AtomicInteger completionCalled = new AtomicInteger();
    final int requestId = request.getRequestId();
    final RequestEntry entry = request.getEntry();
    entry.setNormalCompletion( true );
    entry.setCompletionAction( completionCalled::incrementAndGet );

    final MessageResponse response = new MessageResponse( "" );

    final ChangeSet changeSet = ChangeSet.create( 23, requestId, null, null, null );
    response.recordChangeSet( changeSet, entry );

    connection.setLastRxSequence( 22 );
    connection.setCurrentMessageResponse( response );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    assertFalse( entry.haveResultsArrived() );
    assertEquals( completionCalled.get(), 0 );
    assertEquals( connection.getRequest( requestId ), entry );

    connector.completeMessageResponse();

    assertTrue( entry.haveResultsArrived() );
    assertEquals( connection.getLastRxSequence(), 23 );
    assertNull( connection.getCurrentMessageResponse() );
    assertEquals( completionCalled.get(), 1 );
    assertNull( connection.getRequest( requestId ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( MessageProcessedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getSchemaName(), connector.getSchema().getName() );
      assertEquals( e.getDataLoadStatus().getSequence(), changeSet.getSequence() );
    } );
  }

  @Test
  public void completeMessageResponse_MessageWithRequest_RPCNotComplete()
  {
    final Connector connector = createConnector();
    final Connection connection = newConnection( connector );

    final Request request = connection.newRequest( "SomeAction" );

    final AtomicInteger completionCalled = new AtomicInteger();
    final int requestId = request.getRequestId();
    final RequestEntry entry = request.getEntry();

    final MessageResponse response = new MessageResponse( "" );

    final ChangeSet changeSet = ChangeSet.create( 23, requestId, null, null, null );
    response.recordChangeSet( changeSet, entry );

    connection.setLastRxSequence( 22 );
    connection.setCurrentMessageResponse( response );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    assertFalse( entry.haveResultsArrived() );
    assertEquals( completionCalled.get(), 0 );
    assertEquals( connection.getRequest( requestId ), entry );

    connector.completeMessageResponse();

    assertTrue( entry.haveResultsArrived() );
    assertEquals( connection.getLastRxSequence(), 23 );
    assertNull( connection.getCurrentMessageResponse() );
    assertEquals( completionCalled.get(), 0 );
    assertNull( connection.getRequest( requestId ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( MessageProcessedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getSchemaName(), connector.getSchema().getName() );
      assertEquals( e.getDataLoadStatus().getSequence(), changeSet.getSequence() );
    } );
  }

  @SuppressWarnings( { "ResultOfMethodCallIgnored", "unchecked" } )
  @Test
  public void progressResponseProcessing()
  {
    /*
     * This test steps through each stage of a message processing.
     */

    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.NONE,
                         null,
                         false, true,
                         Collections.emptyList() );
    final EntitySchema.Creator<Linkable> creator = mock( EntitySchema.Creator.class );
    final EntitySchema.Updater<Linkable> updater = mock( EntitySchema.Updater.class );
    final EntitySchema entitySchema =
      new EntitySchema( 0, ValueUtil.randomString(), Linkable.class, creator, updater );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{ entitySchema } );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    @Language( "json" )
    final String rawJsonData =
      "{" +
      "\"last_id\": 1, " +
      // Add Channel 0
      "\"channel_actions\": [ { \"cid\": 0, \"action\": \"add\"} ], " +
      // Add Entity 1 of type 0 from channel 0
      "\"changes\": [{\"id\": 1,\"type\":0,\"channels\":[\"0\"], \"data\":{}}] " +
      "}";
    connection.enqueueResponse( rawJsonData );

    final MessageResponse response = connection.getUnparsedResponses().get( 0 );
    {
      assertNull( connection.getCurrentMessageResponse() );
      assertEquals( connection.getUnparsedResponses().size(), 1 );

      // Select response
      assertTrue( connector.progressResponseProcessing() );

      assertEquals( connection.getCurrentMessageResponse(), response );
      assertEquals( connection.getUnparsedResponses().size(), 0 );
    }

    {
      assertEquals( response.getRawJsonData(), rawJsonData );
      assertThrows( response::getChangeSet );
      assertTrue( response.needsParsing() );
      assertEquals( connection.getPendingResponses().size(), 0 );

      // Parse response
      assertTrue( connector.progressResponseProcessing() );

      assertNull( response.getRawJsonData() );
      assertNotNull( response.getChangeSet() );
      assertFalse( response.needsParsing() );
    }

    {
      assertNull( connection.getCurrentMessageResponse() );
      assertEquals( connection.getPendingResponses().size(), 1 );

      // Pickup parsed response and set it as current
      assertTrue( connector.progressResponseProcessing() );

      assertEquals( connection.getCurrentMessageResponse(), response );
      assertEquals( connection.getPendingResponses().size(), 0 );
    }

    {
      assertTrue( response.needsChannelChangesProcessed() );

      // Process Channel Changes in response
      assertTrue( connector.progressResponseProcessing() );

      assertFalse( response.needsChannelChangesProcessed() );
    }

    {
      assertTrue( response.areEntityChangesPending() );

      when( creator.createEntity( anyInt(), any( EntityChangeData.class ) ) ).thenReturn( mock( Linkable.class ) );

      // Process Entity Changes in response
      assertTrue( connector.progressResponseProcessing() );

      assertFalse( response.areEntityChangesPending() );
    }

    {
      assertTrue( response.areEntityLinksPending() );

      // Process Entity Links in response
      assertTrue( connector.progressResponseProcessing() );

      assertFalse( response.areEntityLinksPending() );
    }

    {
      assertFalse( response.hasWorldBeenValidated() );

      // Validate World
      assertTrue( connector.progressResponseProcessing() );

      assertTrue( response.hasWorldBeenValidated() );
    }

    {
      assertEquals( connection.getCurrentMessageResponse(), response );

      // Complete message
      assertTrue( connector.progressResponseProcessing() );

      assertNull( connection.getCurrentMessageResponse() );
    }
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestAddRequest_onSuccess()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.STATIC,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request =
      new AreaOfInterestRequest( address, AreaOfInterestRequest.Type.ADD, filter );

    pauseScheduler();

    connection.injectCurrentAreaOfInterestRequest( request );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<SafeProcedure> onSuccess = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      assertEquals( i.getArguments()[ 0 ], address );
      onSuccess.set( (SafeProcedure) i.getArguments()[ 2 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestSubscribe( eq( address ),
                         eq( filter ),
                         any( SafeProcedure.class ),
                         any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressAreaOfInterestAddRequest( request );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    assertNull( safeAction( () -> Replicant.context().findSubscription( address ) ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );

    onSuccess.get().call();

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    assertNull( safeAction( () -> Replicant.context().findSubscription( address ) ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( SubscribeCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestAddRequest_onSuccess_CachedValueNotInLocalCache()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.STATIC,
                         null,
                         true, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );
    pauseScheduler();
    connector.pauseMessageScheduler();

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request =
      new AreaOfInterestRequest( address, AreaOfInterestRequest.Type.ADD, filter );

    connection.injectCurrentAreaOfInterestRequest( request );

    Replicant.context().setCacheService( new TestCacheService() );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<SafeProcedure> onSuccess = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      assertEquals( i.getArguments()[ 0 ], address );
      onSuccess.set( (SafeProcedure) i.getArguments()[ 2 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestSubscribe( eq( address ),
                         eq( filter ),
                         any( SafeProcedure.class ),
                         any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressAreaOfInterestAddRequest( request );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    assertNull( safeAction( () -> Replicant.context().findSubscription( address ) ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );

    onSuccess.get().call();

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    assertNull( safeAction( () -> Replicant.context().findSubscription( address ) ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( SubscribeCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestAddRequest_onSuccess_CachedValueInLocalCache()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.STATIC,
                         null,
                         true, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request =
      new AreaOfInterestRequest( address, AreaOfInterestRequest.Type.ADD, filter );

    pauseScheduler();
    connector.pauseMessageScheduler();

    connection.injectCurrentAreaOfInterestRequest( request );

    final TestCacheService cacheService = new TestCacheService();
    Replicant.context().setCacheService( cacheService );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final String key = "1.0";
    final String eTag = "";
    cacheService.store( key, eTag, ValueUtil.randomString() );
    final AtomicReference<SafeProcedure> onCacheValid = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      assertEquals( i.getArguments()[ 0 ], address );
      assertEquals( i.getArguments()[ 2 ], eTag );
      assertNotNull( i.getArguments()[ 3 ] );
      onCacheValid.set( (SafeProcedure) i.getArguments()[ 3 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestSubscribe( eq( address ),
                         eq( filter ),
                         any(),
                         any( SafeProcedure.class ),
                         any( SafeProcedure.class ),
                         any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressAreaOfInterestAddRequest( request );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    assertNull( safeAction( () -> Replicant.context().findSubscription( address ) ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );

    assertFalse( connector.isSchedulerActive() );
    assertEquals( connection.getOutOfBandResponses().size(), 0 );

    onCacheValid.get().call();

    assertTrue( connector.isSchedulerActive() );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    assertEquals( connection.getOutOfBandResponses().size(), 1 );
    assertNull( safeAction( () -> Replicant.context().findSubscription( address ) ) );

    handler.assertEventCount( 1 );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestAddRequest_onFailure()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.STATIC,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request =
      new AreaOfInterestRequest( address, AreaOfInterestRequest.Type.ADD, filter );

    pauseScheduler();

    connection.injectCurrentAreaOfInterestRequest( request );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<Consumer<Throwable>> onFailure = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      assertEquals( i.getArguments()[ 0 ], address );
      onFailure.set( (Consumer<Throwable>) i.getArguments()[ 3 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestSubscribe( eq( address ),
                         eq( filter ),
                         any( SafeProcedure.class ),
                         any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressAreaOfInterestAddRequest( request );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    assertNull( safeAction( () -> Replicant.context().findSubscription( address ) ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );

    final Throwable error = new Throwable();
    onFailure.get().accept( error );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    assertNull( safeAction( () -> Replicant.context().findSubscription( address ) ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( SubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
      assertEquals( e.getError(), error );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressBulkAreaOfInterestAddRequests_onSuccess()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.STATIC,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final ChannelAddress address2 = new ChannelAddress( 1, 0, 2 );
    final ChannelAddress address3 = new ChannelAddress( 1, 0, 3 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.ADD, filter );
    final AreaOfInterestRequest request2 =
      new AreaOfInterestRequest( address2, AreaOfInterestRequest.Type.ADD, filter );
    final AreaOfInterestRequest request3 =
      new AreaOfInterestRequest( address3, AreaOfInterestRequest.Type.ADD, filter );

    pauseScheduler();

    final Subscription subscription1 = createSubscription( address1, null, true );
    final Subscription subscription2 = createSubscription( address2, null, true );
    final Subscription subscription3 = createSubscription( address3, null, true );

    connection.injectCurrentAreaOfInterestRequest( request1 );
    connection.injectCurrentAreaOfInterestRequest( request2 );
    connection.injectCurrentAreaOfInterestRequest( request3 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<SafeProcedure> onSuccess = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      final List<ChannelAddress> addresses = (List<ChannelAddress>) i.getArguments()[ 0 ];
      assertTrue( addresses.contains( address1 ) );
      assertTrue( addresses.contains( address2 ) );
      assertTrue( addresses.contains( address3 ) );
      onSuccess.set( (SafeProcedure) i.getArguments()[ 2 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestBulkSubscribe( anyListOf( ChannelAddress.class ),
                             eq( filter ),
                             any( SafeProcedure.class ),
                             any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressBulkAreaOfInterestAddRequests( Arrays.asList( request1, request2, request3 ) );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription2.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription3.isExplicitSubscription() ) );

    handler.assertEventCount( 3 );
    handler.assertNextEvent( SubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
    } );
    handler.assertNextEvent( SubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
    } );
    handler.assertNextEvent( SubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address3 );
    } );

    onSuccess.get().call();

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription2.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription3.isExplicitSubscription() ) );

    handler.assertEventCount( 6 );
    handler.assertNextEvent( SubscribeCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
    } );
    handler.assertNextEvent( SubscribeCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
    } );
    handler.assertNextEvent( SubscribeCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address3 );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressBulkAreaOfInterestAddRequests_onFailure()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.STATIC,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final ChannelAddress address2 = new ChannelAddress( 1, 0, 2 );
    final ChannelAddress address3 = new ChannelAddress( 1, 0, 3 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.ADD, filter );
    final AreaOfInterestRequest request2 =
      new AreaOfInterestRequest( address2, AreaOfInterestRequest.Type.ADD, filter );
    final AreaOfInterestRequest request3 =
      new AreaOfInterestRequest( address3, AreaOfInterestRequest.Type.ADD, filter );

    pauseScheduler();

    final Subscription subscription1 = createSubscription( address1, null, true );
    final Subscription subscription2 = createSubscription( address2, null, true );
    final Subscription subscription3 = createSubscription( address3, null, true );

    connection.injectCurrentAreaOfInterestRequest( request1 );
    connection.injectCurrentAreaOfInterestRequest( request2 );
    connection.injectCurrentAreaOfInterestRequest( request3 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<Consumer<Throwable>> onFailure = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      final List<ChannelAddress> addresses = (List<ChannelAddress>) i.getArguments()[ 0 ];
      assertTrue( addresses.contains( address1 ) );
      assertTrue( addresses.contains( address2 ) );
      assertTrue( addresses.contains( address3 ) );
      onFailure.set( (Consumer<Throwable>) i.getArguments()[ 3 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestBulkSubscribe( anyListOf( ChannelAddress.class ),
                             eq( filter ),
                             any( SafeProcedure.class ),
                             any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressBulkAreaOfInterestAddRequests( Arrays.asList( request1, request2, request3 ) );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription2.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription3.isExplicitSubscription() ) );

    handler.assertEventCount( 3 );
    handler.assertNextEvent( SubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
    } );
    handler.assertNextEvent( SubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
    } );
    handler.assertNextEvent( SubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address3 );
    } );

    final Throwable error = new Throwable();
    onFailure.get().accept( error );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription2.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription3.isExplicitSubscription() ) );

    handler.assertEventCount( 6 );
    handler.assertNextEvent( SubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
      assertEquals( e.getError(), error );
    } );
    handler.assertNextEvent( SubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
      assertEquals( e.getError(), error );
    } );
    handler.assertNextEvent( SubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address3 );
      assertEquals( e.getError(), error );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestAddRequests_onFailure_singleRequests()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.STATIC,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.ADD, filter );

    pauseScheduler();

    connection.injectCurrentAreaOfInterestRequest( request1 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<Consumer<Throwable>> onFailure = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      onFailure.set( (Consumer<Throwable>) i.getArguments()[ 3 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestSubscribe( eq( address1 ),
                         eq( filter ),
                         any( SafeProcedure.class ),
                         any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    requests.add( request1 );
    connector.progressAreaOfInterestAddRequests( requests );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
    } );

    final Throwable error = new Throwable();
    onFailure.get().accept( error );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( SubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
      assertEquals( e.getError(), error );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestAddRequests_onFailure_zeroRequests()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.STATIC,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.ADD, filter );

    pauseScheduler();

    connection.injectCurrentAreaOfInterestRequest( request1 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    // Pass in empty requests list to simulate that they are all filtered out
    connector.progressAreaOfInterestAddRequests( requests );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    handler.assertEventCount( 0 );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestAddRequests_onFailure_multipleRequests()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.STATIC,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final ChannelAddress address2 = new ChannelAddress( 1, 0, 2 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.ADD, filter );
    final AreaOfInterestRequest request2 =
      new AreaOfInterestRequest( address2, AreaOfInterestRequest.Type.ADD, filter );

    pauseScheduler();
    connector.pauseMessageScheduler();

    connection.injectCurrentAreaOfInterestRequest( request1 );
    connection.injectCurrentAreaOfInterestRequest( request2 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<Consumer<Throwable>> onFailure = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      final List<ChannelAddress> addresses = (List<ChannelAddress>) i.getArguments()[ 0 ];
      assertTrue( addresses.contains( address1 ) );
      assertTrue( addresses.contains( address2 ) );
      onFailure.set( (Consumer<Throwable>) i.getArguments()[ 3 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestBulkSubscribe( anyListOf( ChannelAddress.class ),
                             eq( filter ),
                             any( SafeProcedure.class ),
                             any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    requests.add( request1 );
    requests.add( request2 );
    connector.progressAreaOfInterestAddRequests( requests );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( SubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
    } );
    handler.assertNextEvent( SubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
    } );

    final Throwable error = new Throwable();
    onFailure.get().accept( error );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    handler.assertEventCount( 4 );
    handler.assertNextEvent( SubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
      assertEquals( e.getError(), error );
    } );
    handler.assertNextEvent( SubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
      assertEquals( e.getError(), error );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestUpdateRequest_onSuccess()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.DYNAMIC,
                         mock( SubscriptionUpdateEntityFilter.class ),
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request =
      new AreaOfInterestRequest( address, AreaOfInterestRequest.Type.UPDATE, filter );

    pauseScheduler();

    final Subscription subscription = createSubscription( address, null, true );

    connection.injectCurrentAreaOfInterestRequest( request );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<SafeProcedure> onSuccess = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      assertEquals( i.getArguments()[ 0 ], address );
      onSuccess.set( (SafeProcedure) i.getArguments()[ 2 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestSubscriptionUpdate( eq( address ), eq( filter ), any( SafeProcedure.class ), any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressAreaOfInterestUpdateRequest( request );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription.isExplicitSubscription() ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscriptionUpdateStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );

    onSuccess.get().call();

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription.isExplicitSubscription() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( SubscriptionUpdateCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestUpdateRequest_onFailure()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.DYNAMIC,
                         mock( SubscriptionUpdateEntityFilter.class ),
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request =
      new AreaOfInterestRequest( address, AreaOfInterestRequest.Type.UPDATE, filter );

    pauseScheduler();

    final Subscription subscription = createSubscription( address, null, true );

    connection.injectCurrentAreaOfInterestRequest( request );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<Consumer<Throwable>> onFailure = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      assertEquals( i.getArguments()[ 0 ], address );
      onFailure.set( (Consumer<Throwable>) i.getArguments()[ 3 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestSubscriptionUpdate( eq( address ), eq( filter ), any( SafeProcedure.class ), any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressAreaOfInterestUpdateRequest( request );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription.isExplicitSubscription() ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscriptionUpdateStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );

    final Throwable error = new Throwable();
    onFailure.get().accept( error );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription.isExplicitSubscription() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( SubscriptionUpdateFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
      assertEquals( e.getError(), error );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressBulkAreaOfInterestUpdateRequests_onSuccess()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.DYNAMIC,
                         mock( SubscriptionUpdateEntityFilter.class ),
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final ChannelAddress address2 = new ChannelAddress( 1, 0, 2 );
    final ChannelAddress address3 = new ChannelAddress( 1, 0, 3 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.UPDATE, filter );
    final AreaOfInterestRequest request2 =
      new AreaOfInterestRequest( address2, AreaOfInterestRequest.Type.UPDATE, filter );
    final AreaOfInterestRequest request3 =
      new AreaOfInterestRequest( address3, AreaOfInterestRequest.Type.UPDATE, filter );

    pauseScheduler();

    final Subscription subscription1 = createSubscription( address1, null, true );
    final Subscription subscription2 = createSubscription( address2, null, true );
    final Subscription subscription3 = createSubscription( address3, null, true );

    connection.injectCurrentAreaOfInterestRequest( request1 );
    connection.injectCurrentAreaOfInterestRequest( request2 );
    connection.injectCurrentAreaOfInterestRequest( request3 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<SafeProcedure> onSuccess = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      final List<ChannelAddress> addresses = (List<ChannelAddress>) i.getArguments()[ 0 ];
      assertTrue( addresses.contains( address1 ) );
      assertTrue( addresses.contains( address2 ) );
      assertTrue( addresses.contains( address3 ) );
      onSuccess.set( (SafeProcedure) i.getArguments()[ 2 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestBulkSubscriptionUpdate( anyListOf( ChannelAddress.class ),
                                      eq( filter ),
                                      any( SafeProcedure.class ),
                                      any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressBulkAreaOfInterestUpdateRequests( Arrays.asList( request1, request2, request3 ) );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription2.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription3.isExplicitSubscription() ) );

    handler.assertEventCount( 3 );
    handler.assertNextEvent( SubscriptionUpdateStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
    } );
    handler.assertNextEvent( SubscriptionUpdateStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
    } );
    handler.assertNextEvent( SubscriptionUpdateStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address3 );
    } );

    onSuccess.get().call();

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription2.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription3.isExplicitSubscription() ) );

    handler.assertEventCount( 6 );
    handler.assertNextEvent( SubscriptionUpdateCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
    } );
    handler.assertNextEvent( SubscriptionUpdateCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
    } );
    handler.assertNextEvent( SubscriptionUpdateCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address3 );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressBulkAreaOfInterestUpdateRequests_onFailure()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.DYNAMIC,
                         mock( SubscriptionUpdateEntityFilter.class ),
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final ChannelAddress address2 = new ChannelAddress( 1, 0, 2 );
    final ChannelAddress address3 = new ChannelAddress( 1, 0, 3 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.UPDATE, filter );
    final AreaOfInterestRequest request2 =
      new AreaOfInterestRequest( address2, AreaOfInterestRequest.Type.UPDATE, filter );
    final AreaOfInterestRequest request3 =
      new AreaOfInterestRequest( address3, AreaOfInterestRequest.Type.UPDATE, filter );

    pauseScheduler();

    final Subscription subscription1 = createSubscription( address1, null, true );
    final Subscription subscription2 = createSubscription( address2, null, true );
    final Subscription subscription3 = createSubscription( address3, null, true );

    connection.injectCurrentAreaOfInterestRequest( request1 );
    connection.injectCurrentAreaOfInterestRequest( request2 );
    connection.injectCurrentAreaOfInterestRequest( request3 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<Consumer<Throwable>> onFailure = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      final List<ChannelAddress> addresses = (List<ChannelAddress>) i.getArguments()[ 0 ];
      assertTrue( addresses.contains( address1 ) );
      assertTrue( addresses.contains( address2 ) );
      assertTrue( addresses.contains( address3 ) );
      onFailure.set( (Consumer<Throwable>) i.getArguments()[ 3 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestBulkSubscriptionUpdate( anyListOf( ChannelAddress.class ),
                                      eq( filter ),
                                      any( SafeProcedure.class ),
                                      any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressBulkAreaOfInterestUpdateRequests( Arrays.asList( request1, request2, request3 ) );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription2.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription3.isExplicitSubscription() ) );

    handler.assertEventCount( 3 );
    handler.assertNextEvent( SubscriptionUpdateStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
    } );
    handler.assertNextEvent( SubscriptionUpdateStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
    } );
    handler.assertNextEvent( SubscriptionUpdateStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address3 );
    } );

    final Throwable error = new Throwable();
    onFailure.get().accept( error );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription2.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription3.isExplicitSubscription() ) );

    handler.assertEventCount( 6 );
    handler.assertNextEvent( SubscriptionUpdateFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
      assertEquals( e.getError(), error );
    } );
    handler.assertNextEvent( SubscriptionUpdateFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
      assertEquals( e.getError(), error );
    } );
    handler.assertNextEvent( SubscriptionUpdateFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address3 );
      assertEquals( e.getError(), error );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestUpdateRequests_onFailure_singleRequests()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.DYNAMIC,
                         mock( SubscriptionUpdateEntityFilter.class ),
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.UPDATE, filter );

    pauseScheduler();

    final Subscription subscription1 = createSubscription( address1, null, true );

    connection.injectCurrentAreaOfInterestRequest( request1 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<Consumer<Throwable>> onFailure = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      onFailure.set( (Consumer<Throwable>) i.getArguments()[ 3 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestSubscriptionUpdate( eq( address1 ), eq( filter ), any( SafeProcedure.class ), any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    requests.add( request1 );
    connector.progressAreaOfInterestUpdateRequests( requests );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscriptionUpdateStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
    } );

    final Throwable error = new Throwable();
    onFailure.get().accept( error );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( SubscriptionUpdateFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
      assertEquals( e.getError(), error );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestUpdateRequests_onFailure_zeroRequests()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.DYNAMIC,
                         mock( SubscriptionUpdateEntityFilter.class ),
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.UPDATE, filter );

    pauseScheduler();

    connection.injectCurrentAreaOfInterestRequest( request1 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    // Pass in empty requests list to simulate that they are all filtered out
    connector.progressAreaOfInterestUpdateRequests( requests );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    handler.assertEventCount( 0 );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestUpdateRequests_onFailure_multipleRequests()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.DYNAMIC,
                         mock( SubscriptionUpdateEntityFilter.class ),
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final ChannelAddress address2 = new ChannelAddress( 1, 0, 2 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.UPDATE, filter );
    final AreaOfInterestRequest request2 =
      new AreaOfInterestRequest( address2, AreaOfInterestRequest.Type.UPDATE, filter );

    pauseScheduler();

    final Subscription subscription1 = createSubscription( address1, null, true );
    final Subscription subscription2 = createSubscription( address2, null, true );

    connection.injectCurrentAreaOfInterestRequest( request1 );
    connection.injectCurrentAreaOfInterestRequest( request2 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<Consumer<Throwable>> onFailure = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      final List<ChannelAddress> addresses = (List<ChannelAddress>) i.getArguments()[ 0 ];
      assertTrue( addresses.contains( address1 ) );
      assertTrue( addresses.contains( address2 ) );
      onFailure.set( (Consumer<Throwable>) i.getArguments()[ 3 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestBulkSubscriptionUpdate( anyListOf( ChannelAddress.class ),
                                      eq( filter ),
                                      any( SafeProcedure.class ),
                                      any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    requests.add( request1 );
    requests.add( request2 );
    connector.progressAreaOfInterestUpdateRequests( requests );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription2.isExplicitSubscription() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( SubscriptionUpdateStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
    } );
    handler.assertNextEvent( SubscriptionUpdateStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
    } );

    final Throwable error = new Throwable();
    onFailure.get().accept( error );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription2.isExplicitSubscription() ) );

    handler.assertEventCount( 4 );
    handler.assertNextEvent( SubscriptionUpdateFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
      assertEquals( e.getError(), error );
    } );
    handler.assertNextEvent( SubscriptionUpdateFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
      assertEquals( e.getError(), error );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestRemoveRequest_onSuccess()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.NONE,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final AreaOfInterestRequest request = new AreaOfInterestRequest( address, AreaOfInterestRequest.Type.REMOVE, null );

    pauseScheduler();

    final Subscription subscription = createSubscription( address, null, true );

    connection.injectCurrentAreaOfInterestRequest( request );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<SafeProcedure> onSuccess = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      assertEquals( i.getArguments()[ 0 ], address );
      onSuccess.set( (SafeProcedure) i.getArguments()[ 1 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestUnsubscribe( eq( address ), any( SafeProcedure.class ), any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressAreaOfInterestRemoveRequest( request );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription.isExplicitSubscription() ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( UnsubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );

    onSuccess.get().call();

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertFalse( subscription.isExplicitSubscription() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( UnsubscribeCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestRemoveRequest_onFailure()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         null,
                         ChannelSchema.FilterType.NONE,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address = new ChannelAddress( 1, 0 );
    final AreaOfInterestRequest request = new AreaOfInterestRequest( address, AreaOfInterestRequest.Type.REMOVE, null );

    pauseScheduler();

    final Subscription subscription = createSubscription( address, null, true );

    connection.injectCurrentAreaOfInterestRequest( request );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<Consumer<Throwable>> onFailure = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      assertEquals( i.getArguments()[ 0 ], address );
      onFailure.set( (Consumer<Throwable>) i.getArguments()[ 2 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestUnsubscribe( eq( address ), any( SafeProcedure.class ), any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressAreaOfInterestRemoveRequest( request );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription.isExplicitSubscription() ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( UnsubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
    } );

    final Throwable error = new Throwable();
    onFailure.get().accept( error );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertFalse( subscription.isExplicitSubscription() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( UnsubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address );
      assertEquals( e.getError(), error );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressBulkAreaOfInterestRemoveRequests_onSuccess()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.NONE,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final ChannelAddress address2 = new ChannelAddress( 1, 0, 2 );
    final ChannelAddress address3 = new ChannelAddress( 1, 0, 3 );
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.REMOVE, null );
    final AreaOfInterestRequest request2 =
      new AreaOfInterestRequest( address2, AreaOfInterestRequest.Type.REMOVE, null );
    final AreaOfInterestRequest request3 =
      new AreaOfInterestRequest( address3, AreaOfInterestRequest.Type.REMOVE, null );

    pauseScheduler();

    final Subscription subscription1 = createSubscription( address1, null, true );
    final Subscription subscription2 = createSubscription( address2, null, true );
    final Subscription subscription3 = createSubscription( address3, null, true );

    connection.injectCurrentAreaOfInterestRequest( request1 );
    connection.injectCurrentAreaOfInterestRequest( request2 );
    connection.injectCurrentAreaOfInterestRequest( request3 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<SafeProcedure> onSuccess = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      final List<ChannelAddress> addresses = (List<ChannelAddress>) i.getArguments()[ 0 ];
      assertTrue( addresses.contains( address1 ) );
      assertTrue( addresses.contains( address2 ) );
      assertTrue( addresses.contains( address3 ) );
      onSuccess.set( (SafeProcedure) i.getArguments()[ 1 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestBulkUnsubscribe( anyListOf( ChannelAddress.class ), any( SafeProcedure.class ), any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressBulkAreaOfInterestRemoveRequests( Arrays.asList( request1, request2, request3 ) );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription2.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription3.isExplicitSubscription() ) );

    handler.assertEventCount( 3 );
    handler.assertNextEvent( UnsubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
    } );
    handler.assertNextEvent( UnsubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
    } );
    handler.assertNextEvent( UnsubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address3 );
    } );

    onSuccess.get().call();

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertFalse( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertFalse( subscription2.isExplicitSubscription() ) );
    safeAction( () -> assertFalse( subscription3.isExplicitSubscription() ) );

    handler.assertEventCount( 6 );
    handler.assertNextEvent( UnsubscribeCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
    } );
    handler.assertNextEvent( UnsubscribeCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
    } );
    handler.assertNextEvent( UnsubscribeCompletedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address3 );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressBulkAreaOfInterestRemoveRequests_onFailure()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.NONE,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final ChannelAddress address2 = new ChannelAddress( 1, 0, 2 );
    final ChannelAddress address3 = new ChannelAddress( 1, 0, 3 );
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.REMOVE, null );
    final AreaOfInterestRequest request2 =
      new AreaOfInterestRequest( address2, AreaOfInterestRequest.Type.REMOVE, null );
    final AreaOfInterestRequest request3 =
      new AreaOfInterestRequest( address3, AreaOfInterestRequest.Type.REMOVE, null );

    pauseScheduler();

    final Subscription subscription1 = createSubscription( address1, null, true );
    final Subscription subscription2 = createSubscription( address2, null, true );
    final Subscription subscription3 = createSubscription( address3, null, true );

    connection.injectCurrentAreaOfInterestRequest( request1 );
    connection.injectCurrentAreaOfInterestRequest( request2 );
    connection.injectCurrentAreaOfInterestRequest( request3 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<Consumer<Throwable>> onFailure = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      final List<ChannelAddress> addresses = (List<ChannelAddress>) i.getArguments()[ 0 ];
      assertTrue( addresses.contains( address1 ) );
      assertTrue( addresses.contains( address2 ) );
      assertTrue( addresses.contains( address3 ) );
      onFailure.set( (Consumer<Throwable>) i.getArguments()[ 2 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestBulkUnsubscribe( anyListOf( ChannelAddress.class ), any( SafeProcedure.class ), any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressBulkAreaOfInterestRemoveRequests( Arrays.asList( request1, request2, request3 ) );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription2.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription3.isExplicitSubscription() ) );

    handler.assertEventCount( 3 );
    handler.assertNextEvent( UnsubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
    } );
    handler.assertNextEvent( UnsubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
    } );
    handler.assertNextEvent( UnsubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address3 );
    } );

    final Throwable error = new Throwable();
    onFailure.get().accept( error );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertFalse( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertFalse( subscription2.isExplicitSubscription() ) );
    safeAction( () -> assertFalse( subscription3.isExplicitSubscription() ) );

    handler.assertEventCount( 6 );
    handler.assertNextEvent( UnsubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
      assertEquals( e.getError(), error );
    } );
    handler.assertNextEvent( UnsubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
      assertEquals( e.getError(), error );
    } );
    handler.assertNextEvent( UnsubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address3 );
      assertEquals( e.getError(), error );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestRemoveRequests_onFailure_singleRequests()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.NONE,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.REMOVE, null );

    pauseScheduler();

    final Subscription subscription1 = createSubscription( address1, null, true );

    connection.injectCurrentAreaOfInterestRequest( request1 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<Consumer<Throwable>> onFailure = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      onFailure.set( (Consumer<Throwable>) i.getArguments()[ 2 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestUnsubscribe( eq( address1 ), any( SafeProcedure.class ), any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    requests.add( request1 );
    connector.progressAreaOfInterestRemoveRequests( requests );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( UnsubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
    } );

    final Throwable error = new Throwable();
    onFailure.get().accept( error );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertFalse( subscription1.isExplicitSubscription() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( UnsubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
      assertEquals( e.getError(), error );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestRemoveRequests_onFailure_zeroRequests()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.NONE,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.REMOVE, null );

    pauseScheduler();

    connection.injectCurrentAreaOfInterestRequest( request1 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    // Pass in empty requests list to simulate that they are all filtered out
    connector.progressAreaOfInterestRemoveRequests( requests );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    handler.assertEventCount( 0 );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestRemoveRequests_onFailure_multipleRequests()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.NONE,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final ChannelAddress address2 = new ChannelAddress( 1, 0, 2 );
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.REMOVE, null );
    final AreaOfInterestRequest request2 =
      new AreaOfInterestRequest( address2, AreaOfInterestRequest.Type.REMOVE, null );

    pauseScheduler();

    final Subscription subscription1 = createSubscription( address1, null, true );
    final Subscription subscription2 = createSubscription( address2, null, true );

    connection.injectCurrentAreaOfInterestRequest( request1 );
    connection.injectCurrentAreaOfInterestRequest( request2 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    final AtomicReference<Consumer<Throwable>> onFailure = new AtomicReference<>();
    final AtomicInteger callCount = new AtomicInteger();
    doAnswer( i -> {
      callCount.incrementAndGet();
      final List<ChannelAddress> addresses = (List<ChannelAddress>) i.getArguments()[ 0 ];
      assertTrue( addresses.contains( address1 ) );
      assertTrue( addresses.contains( address2 ) );
      onFailure.set( (Consumer<Throwable>) i.getArguments()[ 2 ] );
      return null;
    } )
      .when( connector.getTransport() )
      .requestBulkUnsubscribe( anyListOf( ChannelAddress.class ), any( SafeProcedure.class ), any() );

    assertEquals( callCount.get(), 0 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    final ArrayList<AreaOfInterestRequest> requests = new ArrayList<>();
    requests.add( request1 );
    requests.add( request2 );
    connector.progressAreaOfInterestRemoveRequests( requests );

    assertEquals( callCount.get(), 1 );
    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertTrue( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertTrue( subscription2.isExplicitSubscription() ) );

    handler.assertEventCount( 2 );
    handler.assertNextEvent( UnsubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
    } );
    handler.assertNextEvent( UnsubscribeStartedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
    } );

    final Throwable error = new Throwable();
    onFailure.get().accept( error );

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );
    safeAction( () -> assertFalse( subscription1.isExplicitSubscription() ) );
    safeAction( () -> assertFalse( subscription2.isExplicitSubscription() ) );

    handler.assertEventCount( 4 );
    handler.assertNextEvent( UnsubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address1 );
      assertEquals( e.getError(), error );
    } );
    handler.assertNextEvent( UnsubscribeFailedEvent.class, e -> {
      assertEquals( e.getSchemaId(), connector.getSchema().getId() );
      assertEquals( e.getAddress(), address2 );
      assertEquals( e.getError(), error );
    } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestRequestProcessing_Noop()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.STATIC,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    pauseScheduler();

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressAreaOfInterestRequestProcessing();

    assertTrue( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    handler.assertEventCount( 0 );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestRequestProcessing_InProgress()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.STATIC,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.ADD, filter );

    pauseScheduler();

    request1.markAsInProgress();

    connection.injectCurrentAreaOfInterestRequest( request1 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressAreaOfInterestRequestProcessing();

    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    handler.assertEventCount( 0 );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestRequestProcessing_Add()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.STATIC,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.ADD, filter );

    pauseScheduler();

    connection.injectCurrentAreaOfInterestRequest( request1 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressAreaOfInterestRequestProcessing();

    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscribeStartedEvent.class, e -> assertEquals( e.getAddress(), address1 ) );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestRequestProcessing_Update()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.DYNAMIC,
                         mock( SubscriptionUpdateEntityFilter.class ),
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final String filter = ValueUtil.randomString();
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.UPDATE, filter );

    pauseScheduler();

    createSubscription( address1, null, true );
    connection.injectCurrentAreaOfInterestRequest( request1 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressAreaOfInterestRequestProcessing();

    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SubscriptionUpdateStartedEvent.class, e -> assertEquals( e.getAddress(), address1 ) );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void progressAreaOfInterestRequestProcessing_Remove()
  {
    final ChannelSchema channelSchema =
      new ChannelSchema( 0,
                         ValueUtil.randomString(),
                         String.class,
                         ChannelSchema.FilterType.NONE,
                         null,
                         false, true,
                         Collections.emptyList() );
    final SystemSchema schema =
      new SystemSchema( 1,
                        ValueUtil.randomString(),
                        new ChannelSchema[]{ channelSchema },
                        new EntitySchema[]{} );

    final Connector connector = createConnector( schema );
    final Connection connection = newConnection( connector );

    final ChannelAddress address1 = new ChannelAddress( 1, 0, 1 );
    final AreaOfInterestRequest request1 =
      new AreaOfInterestRequest( address1, AreaOfInterestRequest.Type.REMOVE, null );

    pauseScheduler();

    createSubscription( address1, null, true );

    connection.injectCurrentAreaOfInterestRequest( request1 );

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    connector.progressAreaOfInterestRequestProcessing();

    assertFalse( connection.getCurrentAreaOfInterestRequests().isEmpty() );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( UnsubscribeStartedEvent.class, e -> assertEquals( e.getAddress(), address1 ) );
  }

  @Test
  public void pauseMessageScheduler()
  {
    final Connector connector = createConnector();
    newConnection( connector );

    connector.pauseMessageScheduler();

    assertTrue( connector.isSchedulerPaused() );
    assertFalse( connector.isSchedulerActive() );

    connector.requestSubscribe( new ChannelAddress( 1, 0, 1 ), null );

    assertTrue( connector.isSchedulerActive() );

    connector.resumeMessageScheduler();

    assertFalse( connector.isSchedulerPaused() );
    assertFalse( connector.isSchedulerActive() );

    connector.pauseMessageScheduler();

    assertTrue( connector.isSchedulerPaused() );
    assertFalse( connector.isSchedulerActive() );

    // No progress
    assertFalse( connector.progressMessages() );

    Disposable.dispose( connector );

    assertFalse( connector.isSchedulerActive() );
    assertTrue( connector.isSchedulerPaused() );

    assertNull( connector.getSchedulerLock() );
  }

  @Test
  public void isSynchronized_notConnected()
  {
    final Connector connector = createConnector();
    safeAction( () -> connector.setState( ConnectorState.DISCONNECTED ) );
    safeAction( () -> assertFalse( connector.isSynchronized() ) );
  }

  @Test
  public void isSynchronized_connected()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );
    safeAction( () -> assertTrue( connector.isSynchronized() ) );
  }

  @Test
  public void isSynchronized_sentRequest_NotSynced()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );
    safeAction( () -> connector.setLastTxRequestId( 2 ) );
    safeAction( () -> assertFalse( connector.isSynchronized() ) );
  }

  @Test
  public void isSynchronized_receivedRequestResponse_NotSynced()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );
    safeAction( () -> connector.setLastTxRequestId( 2 ) );
    safeAction( () -> connector.setLastRxRequestId( 2 ) );
    safeAction( () -> assertFalse( connector.isSynchronized() ) );
  }

  @Test
  public void isSynchronized_sentSyncRequest_NotSynced()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );
    safeAction( () -> connector.setLastTxRequestId( 2 ) );
    safeAction( () -> connector.setLastRxRequestId( 2 ) );
    safeAction( () -> connector.setLastSyncTxRequestId( 2 ) );
    safeAction( () -> connector.setSyncInFlight( true ) );
    safeAction( () -> assertFalse( connector.isSynchronized() ) );
  }

  @Test
  public void isSynchronized_receivedSyncRequestResponse_Synced()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );
    safeAction( () -> connector.setLastTxRequestId( 2 ) );
    safeAction( () -> connector.setLastRxRequestId( 2 ) );
    safeAction( () -> connector.setLastSyncTxRequestId( 2 ) );
    safeAction( () -> connector.setLastSyncRxRequestId( 2 ) );
    safeAction( () -> connector.setSyncInFlight( false ) );
    safeAction( () -> assertTrue( connector.isSynchronized() ) );
  }

  @Test
  public void isSynchronized_receivedSyncRequestResponseButResponsesQueued_NotSynced()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );
    safeAction( () -> connector.setLastTxRequestId( 2 ) );
    safeAction( () -> connector.setLastRxRequestId( 2 ) );
    safeAction( () -> connector.setLastSyncTxRequestId( 2 ) );
    safeAction( () -> connector.setLastSyncRxRequestId( 2 ) );
    safeAction( () -> connector.setSyncInFlight( false ) );
    safeAction( () -> connector.setPendingResponseQueueEmpty( false ) );
    safeAction( () -> assertFalse( connector.isSynchronized() ) );
  }

  @Test
  public void isSynchronized_receivedSyncRequestResponseErrored_NotSynced()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );
    safeAction( () -> connector.setLastTxRequestId( 2 ) );
    safeAction( () -> connector.setLastRxRequestId( 2 ) );
    safeAction( () -> connector.setLastSyncTxRequestId( 2 ) );
    safeAction( () -> connector.setSyncInFlight( false ) );
    safeAction( () -> connector.setPendingResponseQueueEmpty( true ) );
    safeAction( () -> assertFalse( connector.isSynchronized() ) );
  }

  @Test
  public void shouldRequestSync_notConnected()
  {
    final Connector connector = createConnector();
    safeAction( () -> connector.setState( ConnectorState.DISCONNECTED ) );
    safeAction( () -> assertFalse( connector.shouldRequestSync() ) );
  }

  @Test
  public void shouldRequestSync_connected()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );
    safeAction( () -> assertFalse( connector.shouldRequestSync() ) );
  }

  @Test
  public void shouldRequestSync_sentRequest_NotSynced()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );
    safeAction( () -> connector.setLastTxRequestId( 2 ) );
    safeAction( () -> assertFalse( connector.shouldRequestSync() ) );
  }

  @Test
  public void shouldRequestSync_receivedRequestResponse_NotSynced()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );
    safeAction( () -> connector.setLastTxRequestId( 2 ) );
    safeAction( () -> connector.setLastRxRequestId( 2 ) );
    safeAction( () -> assertTrue( connector.shouldRequestSync() ) );
  }

  @Test
  public void shouldRequestSync_sentSyncRequest_NotSynced()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );
    safeAction( () -> connector.setLastTxRequestId( 2 ) );
    safeAction( () -> connector.setLastRxRequestId( 2 ) );
    safeAction( () -> connector.setLastSyncTxRequestId( 2 ) );
    safeAction( () -> connector.setSyncInFlight( true ) );
    safeAction( () -> assertFalse( connector.shouldRequestSync() ) );
  }

  @Test
  public void shouldRequestSync_receivedSyncRequestResponse_Synced()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );
    safeAction( () -> connector.setLastTxRequestId( 2 ) );
    safeAction( () -> connector.setLastRxRequestId( 2 ) );
    safeAction( () -> connector.setLastSyncTxRequestId( 2 ) );
    safeAction( () -> connector.setLastSyncRxRequestId( 2 ) );
    safeAction( () -> connector.setSyncInFlight( false ) );
    safeAction( () -> assertFalse( connector.shouldRequestSync() ) );
  }

  @Test
  public void shouldRequestSync_receivedSyncRequestResponseButResponsesQueued_NotSynced()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );
    safeAction( () -> connector.setLastTxRequestId( 2 ) );
    safeAction( () -> connector.setLastRxRequestId( 2 ) );
    safeAction( () -> connector.setLastSyncTxRequestId( 2 ) );
    safeAction( () -> connector.setLastSyncRxRequestId( 2 ) );
    safeAction( () -> connector.setSyncInFlight( false ) );
    safeAction( () -> connector.setPendingResponseQueueEmpty( false ) );
    safeAction( () -> assertFalse( connector.shouldRequestSync() ) );
  }

  @Test
  public void shouldRequestSync_receivedSyncRequestResponseErrored_NotSynced()
  {
    final Connector connector = createConnector();
    newConnection( connector );
    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );
    safeAction( () -> connector.setLastTxRequestId( 2 ) );
    safeAction( () -> connector.setLastRxRequestId( 2 ) );
    safeAction( () -> connector.setLastSyncTxRequestId( 2 ) );
    safeAction( () -> connector.setSyncInFlight( false ) );
    safeAction( () -> connector.setPendingResponseQueueEmpty( true ) );
    safeAction( () -> assertTrue( connector.shouldRequestSync() ) );
  }

  @Test
  public void onInSync()
  {
    final Connector connector = createConnector();

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    safeAction( () -> connector.setSyncInFlight( true ) );
    connector.onInSync();
    safeAction( () -> assertFalse( connector.isSyncInFlight() ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( InSyncEvent.class,
                             e -> assertEquals( e.getSchemaId(), connector.getSchema().getId() ) );
  }

  @Test
  public void onOutOfSync()
  {
    final Connector connector = createConnector();

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    safeAction( () -> connector.setSyncInFlight( true ) );
    connector.onOutOfSync();
    safeAction( () -> assertFalse( connector.isSyncInFlight() ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( OutOfSyncEvent.class,
                             e -> assertEquals( e.getSchemaId(), connector.getSchema().getId() ) );
  }

  @Test
  public void onSyncError()
  {
    final Connector connector = createConnector();

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    safeAction( () -> connector.setSyncInFlight( true ) );
    final Throwable error = new Throwable();
    connector.onSyncError( error );
    safeAction( () -> assertFalse( connector.isSyncInFlight() ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SyncFailureEvent.class,
                             e -> {
                               assertEquals( e.getSchemaId(), connector.getSchema().getId() );
                               assertEquals( e.getError(), error );
                             } );
  }

  @SuppressWarnings( "unchecked" )
  @Test
  public void requestSync()
  {
    final Connector connector = createConnector();

    final TestSpyEventHandler handler = registerTestSpyEventHandler();

    safeAction( () -> assertFalse( connector.isSyncInFlight() ) );
    connector.requestSync();
    safeAction( () -> assertTrue( connector.isSyncInFlight() ) );

    verify( connector.getTransport() ).requestSync( any( SafeProcedure.class ),
                                                    any( SafeProcedure.class ),
                                                    (Consumer<Throwable>) any( Consumer.class ) );

    handler.assertEventCount( 1 );
    handler.assertNextEvent( SyncRequestEvent.class,
                             e -> assertEquals( e.getSchemaId(), connector.getSchema().getId() ) );
  }

  @Test
  public void maybeRequestSync()
  {
    final Connector connector = createConnector();
    newConnection( connector );

    safeAction( () -> connector.setState( ConnectorState.CONNECTED ) );

    connector.maybeRequestSync();

    safeAction( () -> assertFalse( connector.isSyncInFlight() ) );

    safeAction( () -> connector.setLastTxRequestId( 2 ) );
    safeAction( () -> connector.setLastRxRequestId( 2 ) );
    safeAction( () -> assertTrue( connector.shouldRequestSync() ) );

    connector.maybeRequestSync();

    safeAction( () -> assertTrue( connector.isSyncInFlight() ) );
  }
}
