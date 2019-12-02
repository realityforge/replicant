package org.realityforge.replicant.client;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class EntitySubscriptionManagerTest
{
  @Test
  public void typeGraphRegistrations()
  {
    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    assertNull( sm.findSubscription( new ChannelAddress( G.G1 ) ) );

    assertEquals( sm.getTypeSubscriptions().size(), 0 );
    assertFalse( sm.getTypeSubscriptions().contains( G.G1 ) );

    final boolean explicitSubscription = true;
    sm.recordSubscription( new ChannelAddress( G.G1 ), null, explicitSubscription );

    assertEquals( sm.getTypeSubscriptions().size(), 1 );
    assertTrue( sm.getTypeSubscriptions().contains( G.G1 ) );

    final ChannelSubscriptionEntry s = sm.findSubscription( new ChannelAddress( G.G1 ) );
    assertNotNull( s );
    assertNotNull( sm.getSubscription( new ChannelAddress( G.G1 ) ) );
    assertEquals( s.getDescriptor(), new ChannelAddress( G.G1 ) );
    assertEquals( s.isExplicitSubscription(), explicitSubscription );
    assertEquals( s.getEntities().size(), 0 );

    sm.removeSubscription( new ChannelAddress( G.G1 ) );
    assertNull( sm.findSubscription( new ChannelAddress( G.G1 ) ) );
  }

  @Test
  public void instanceGraphRegistrations()
  {
    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    assertNull( sm.findSubscription( new ChannelAddress( G.G2, 1 ) ) );

    assertEquals( sm.getInstanceSubscriptionKeys().size(), 0 );
    assertEquals( sm.getInstanceSubscriptions( G.G2 ).size(), 0 );

    final boolean explicitSubscription = false;
    sm.recordSubscription( new ChannelAddress( G.G2, 1 ), null, explicitSubscription );

    assertEquals( sm.getInstanceSubscriptionKeys().size(), 1 );
    assertEquals( sm.getInstanceSubscriptions( G.G2 ).size(), 1 );

    assertNull( sm.findSubscription( new ChannelAddress( G.G2, 2 ) ) );
    final ChannelSubscriptionEntry s = sm.findSubscription( new ChannelAddress( G.G2, 1 ) );
    assertNotNull( s );
    assertNotNull( sm.getSubscription( new ChannelAddress( G.G2, 1 ) ) );
    assertEquals( s.getDescriptor(), new ChannelAddress( G.G2, 1 ) );
    assertEquals( s.isExplicitSubscription(), explicitSubscription );
    assertEquals( s.getEntities().size(), 0 );

    sm.removeSubscription( new ChannelAddress( G.G2, 1 ) );
    assertNull( sm.findSubscription( new ChannelAddress( G.G2, 1 ) ) );
  }

  @Test
  public void entitySubscriptionInTypeGraph()
  {
    final Class<A> type = A.class;
    final Object id = 1;

    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    assertNull( sm.findSubscription( new ChannelAddress( G.G1 ) ) );
    final ChannelSubscriptionEntry e1 = sm.recordSubscription( new ChannelAddress( G.G1 ), null, false );

    assertEntityNotSubscribed( sm, new ChannelAddress( G.G1, null ), type, id );

    sm.updateEntity( type,
                     id,
                     new ChannelAddress[]{ new ChannelAddress( G.G1 ) } );
    assertEntitySubscribed( sm, new ChannelAddress( G.G1, null ), type, id );
    sm.updateEntity( type,
                     id,
                     new ChannelAddress[]{ new ChannelAddress( G.G1 ) } );

    final ChannelSubscriptionEntry e2 = sm.removeSubscription( new ChannelAddress( G.G1 ) );

    assertEntityNotSubscribed( sm, new ChannelAddress( G.G1, null ), type, id );

    assertEquals( e2, e1 );
  }

  @Test
  public void entitySubscriptionInTypeGraph_removeEntity()
  {
    final Class<A> type = A.class;
    final Object id = 1;

    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    assertNull( sm.findSubscription( new ChannelAddress( G.G1 ) ) );
    sm.recordSubscription( new ChannelAddress( G.G1 ), null, false );

    sm.updateEntity( type,
                     id,
                     new ChannelAddress[]{ new ChannelAddress( G.G1 ) } );

    assertEntitySubscribed( sm, new ChannelAddress( G.G1, null ), type, id );

    sm.updateEntity( type,
                     id,
                     new ChannelAddress[]{ new ChannelAddress( G.G1 ) } );

    assertEntitySubscribed( sm, new ChannelAddress( G.G1, null ), type, id );

    sm.removeEntity( type, id );
    assertEntityNotSubscribed( sm, new ChannelAddress( G.G1, null ), type, id );

    //assertEntityPresent( type, id, r );

    sm.removeSubscription( new ChannelAddress( G.G1 ));

    // Entity still here as unsubscribe did not unload as removed from subscription manager
    //assertEntityPresent( type, id, r );
  }

  @Test
  public void entitySubscriptionInInstanceGraph()
  {
    final Class<A> type = A.class;
    final Object id = 1;

    final EntityRepository r = new EntityRepositoryImpl();
    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    assertNull( sm.findSubscription( new ChannelAddress( G.G2, 1 ) ) );
    sm.recordSubscription( new ChannelAddress( G.G2, 1 ), null, false );

    r.registerEntity( type, id, new A() );
    assertEntityNotSubscribed( sm, new ChannelAddress( G.G2, 1 ), type, id );

    sm.updateEntity( type,
                     id,
                     new ChannelAddress[]{ new ChannelAddress( G.G2, 1 ) } );
    assertEntitySubscribed( sm, new ChannelAddress( G.G2, 1 ), type, id );
    sm.updateEntity( type,
                     id,
                     new ChannelAddress[]{ new ChannelAddress( G.G2, 1 ) } );

    sm.removeSubscription( new ChannelAddress( G.G2, 1 ) );

    assertEntityNotSubscribed( sm, new ChannelAddress( G.G2, 1 ), type, id );
  }

  @Test
  public void entitySubscriptionAndUpdateInInstanceGraph()
  {
    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    assertNull( sm.findSubscription( new ChannelAddress( G.G2, 1 ) ) );

    final ChannelSubscriptionEntry e1 = sm.recordSubscription( new ChannelAddress( G.G2, 1 ), "F1", false );

    assertEquals( sm.getSubscription( new ChannelAddress( G.G2, 1 ) ).getFilter(), "F1" );

    final ChannelSubscriptionEntry e2 = sm.updateSubscription( new ChannelAddress( G.G2, 1 ), "F2" );

    assertEquals( sm.getSubscription( new ChannelAddress( G.G2, 1 ) ).getFilter(), "F2" );

    assertEquals( e1, e2 );
  }

  @Test
  public void entitySubscriptionAndUpdateInTypeGraph()
  {
    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    assertNull( sm.findSubscription( new ChannelAddress( G.G1 ) ) );

    final ChannelSubscriptionEntry e1 = sm.recordSubscription( new ChannelAddress( G.G1 ), "F1", false );

    assertEquals( sm.getSubscription( new ChannelAddress( G.G1 ) ).getFilter(), "F1" );

    final ChannelSubscriptionEntry e2 = sm.updateSubscription( new ChannelAddress( G.G1 ), "F2" );

    assertEquals( sm.getSubscription( new ChannelAddress( G.G1 ) ).getFilter(), "F2" );

    assertEquals( e1, e2 );
  }

  @Test
  public void entitySubscriptionInInstanceGraph_removeEntity()
  {
    final Class<A> type = A.class;
    final Object id = 1;

    final EntityRepository r = new EntityRepositoryImpl();
    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    assertNull( sm.findSubscription( new ChannelAddress( G.G2, 1 ) ) );
    sm.recordSubscription( new ChannelAddress( G.G2, 1 ), null, false );

    r.registerEntity( type, id, new A() );
    sm.updateEntity( type,
                     id,
                     new ChannelAddress[]{ new ChannelAddress( G.G2, 1 ) } );

    assertEntitySubscribed( sm, new ChannelAddress( G.G2, 1 ), type, id );

    sm.updateEntity( type,
                     id,
                     new ChannelAddress[]{ new ChannelAddress( G.G2, 1 ) } );

    assertEntitySubscribed( sm, new ChannelAddress( G.G2, 1 ), type, id );

    sm.removeEntity( type, id );
    assertEntityNotSubscribed( sm, new ChannelAddress( G.G2, 1 ), type, id );

    sm.removeSubscription( new ChannelAddress( G.G2, 1 ) );
  }

  @Test
  public void entityOverlappingSubscriptions()
  {
    final Class<A> type = A.class;
    final Object id = 1;

    final EntityRepository r = new EntityRepositoryImpl();
    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    assertNull( sm.findSubscription( new ChannelAddress( G.G1 ) ) );
    assertNull( sm.findSubscription( new ChannelAddress( G.G2, 1 ) ) );
    sm.recordSubscription( new ChannelAddress( G.G1 ), null, false );
    sm.recordSubscription( new ChannelAddress( G.G2, 1 ), null, false );

    r.registerEntity( type, id, new A() );

    assertEntityNotSubscribed( sm, new ChannelAddress( G.G1, null ), type, id );
    assertEntityNotSubscribed( sm, new ChannelAddress( G.G2, 1 ), type, id );

    sm.updateEntity( type,
                     id,
                     new ChannelAddress[]{ new ChannelAddress( G.G1 ) } );
    assertEntitySubscribed( sm, new ChannelAddress( G.G1, null ), type, id );
    assertEntityNotSubscribed( sm, new ChannelAddress( G.G2, 1 ), type, id );
    sm.updateEntity( type,
                     id,
                     new ChannelAddress[]{ new ChannelAddress( G.G2, 1 ) } );

    assertEntitySubscribed( sm, new ChannelAddress( G.G1, null ), type, id );
    assertEntitySubscribed( sm, new ChannelAddress( G.G2, 1 ), type, id );

    sm.removeSubscription( new ChannelAddress( G.G1) );

    assertEntityNotSubscribed( sm, new ChannelAddress( G.G1, null ), type, id );
    assertEntitySubscribed( sm, new ChannelAddress( G.G2, 1 ), type, id );

    sm.removeSubscription( new ChannelAddress( G.G2, 1) );

    assertEntityNotSubscribed( sm, new ChannelAddress( G.G1, null ), type, id );
    assertEntityNotSubscribed( sm, new ChannelAddress( G.G2, 1 ), type, id );
  }

  @Test
  public void removeEntityFromGraph()
  {
    final Class<A> type = A.class;
    final Object id = 1;

    final EntityRepository r = new EntityRepositoryImpl();
    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    assertNull( sm.findSubscription( new ChannelAddress( G.G1 ) ) );
    assertNull( sm.findSubscription( new ChannelAddress( G.G2 ) ) );

    final ChannelSubscriptionEntry s1 = sm.recordSubscription( new ChannelAddress( G.G1 ), "X", false );
    assertEquals( s1.getFilter(), "X" );
    final ChannelSubscriptionEntry s2 = sm.recordSubscription( new ChannelAddress( G.G2 ), null, false );
    assertNull( s2.getFilter() );

    r.registerEntity( type, id, new A() );

    assertEntityNotSubscribed( sm, new ChannelAddress( G.G1, null ), type, id );
    assertEntityNotSubscribed( sm, new ChannelAddress( G.G2, null ), type, id );

    sm.updateEntity( type,
                     id,
                     new ChannelAddress[]{ new ChannelAddress( G.G1 ) } );
    assertEntitySubscribed( sm, new ChannelAddress( G.G1, null ), type, id );
    assertEntityNotSubscribed( sm, new ChannelAddress( G.G2, null ), type, id );

    sm.updateEntity( type,
                     id,
                     new ChannelAddress[]{ new ChannelAddress( G.G1 ),
                                           new ChannelAddress( G.G2 ) } );

    assertEntitySubscribed( sm, new ChannelAddress( G.G1, null ), type, id );
    assertEntitySubscribed( sm, new ChannelAddress( G.G2, null ), type, id );

    sm.removeEntityFromGraph( type, id, new ChannelAddress( G.G1 ) );

    assertEntityNotSubscribed( sm, new ChannelAddress( G.G1, null ), type, id );
    assertEntitySubscribed( sm, new ChannelAddress( G.G2, null ), type, id );

    final EntitySubscriptionEntry e =
      sm.removeEntityFromGraph( type, id, new ChannelAddress( G.G2 ) );

    assertEntityNotSubscribed( sm, new ChannelAddress( G.G1, null ), type, id );
    assertEntityNotSubscribed( sm, new ChannelAddress( G.G2, null ), type, id );

    assertEquals( e.getGraphSubscriptions().size(), 0 );
  }

  private void assertEntitySubscribed( final EntitySubscriptionManager sm,
                                       final ChannelAddress descriptor,
                                       final Class<A> type, final Object id )
  {
    final ChannelSubscriptionEntry entry = sm.getSubscription( descriptor );
    assertNotNull( entry.getEntities().get( type ).get( id ) );
    assertNotNull( sm.getSubscription( type, id ).getGraphSubscriptions().get( descriptor ) );
  }

  private void assertEntityNotSubscribed( final EntitySubscriptionManager sm,
                                          final ChannelAddress descriptor,
                                          final Class<A> type, final Object id )
  {
    boolean found;
    try
    {
      final ChannelSubscriptionEntry subscription = sm.getSubscription( descriptor );
      assertNotNull( subscription.getEntities().get( type ).get( id ) );
      found = true;
    }
    catch ( final Throwable t )
    {
      found = false;
    }
    assertFalse( found, "Found subscription unexpectedly" );
  }

  @Test( expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = "Graph already subscribed: .*" )
  public void subscribe_nonExistentTypeGraph()
  {
    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    sm.recordSubscription( new ChannelAddress( G.G1 ), null, false );
    sm.recordSubscription( new ChannelAddress( G.G1 ), null, false );
  }

  @Test( expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = "Graph not subscribed: .*" )
  public void getSubscription_nonExistentTypeGraph()
  {
    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    sm.getSubscription( new ChannelAddress( G.G1 ) );
  }

  @Test( expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = "Graph not subscribed: .*" )
  public void unsubscribe_nonExistentTypeGraph()
  {
    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    sm.removeSubscription( new ChannelAddress( G.G1 ) );
  }

  @Test( expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = "Graph already subscribed: .*:1" )
  public void subscribe_nonExistentInstanceGraph()
  {
    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    sm.recordSubscription( new ChannelAddress( G.G1, "1" ), null, false );
    sm.recordSubscription( new ChannelAddress( G.G1, "1" ), null, false );
  }

  @Test( expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = "Graph not subscribed: .*" )
  public void getSubscription_nonExistentInstanceGraph()
  {
    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    sm.getSubscription( new ChannelAddress( G.G1, "1" ) );
  }

  @Test( expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = "Graph not subscribed: .*" )
  public void unsubscribe_nonExistentInstanceGraph()
  {
    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    sm.removeSubscription( new ChannelAddress( G.G1, "1" ) );
  }

  @Test( expectedExceptions = IllegalStateException.class,
    expectedExceptionsMessageRegExp = "Graph not subscribed: .*" )
  public void unsubscribe_nonExistentInstanceGraph_whenTypeCreated()
  {
    final EntitySubscriptionManager sm = new EntitySubscriptionManagerImpl();
    sm.recordSubscription( new ChannelAddress( G.G1 ), "1", false );
    sm.removeSubscription( new ChannelAddress( G.G1, "2" ) );
  }

  enum G
  {
    G1, G2
  }

  static class A
  {
  }
}
