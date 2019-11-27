package org.realityforge.replicant.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

@SuppressWarnings( "Duplicates" )
public class EntityRepositoryTest
{
  @Test
  public void registerAndDeregisterHasExpectedEffects()
  {
    final EntityRepository r = new EntityRepositoryImpl();
    final Class<B> parentType = B.class;
    final Class<A> type = A.class;
    final String id = "A";
    final A entity = new A();

    // If I register an ID it should be accessible in all weird and wonderful ways
    r.registerEntity( type, id, entity );

    assertNotLinked( entity );

    assertEquals( r.findByID( type, id, false ), entity );
    assertNotLinked( entity );
    assertEquals( r.findByID( type, id, true ), entity );
    assertLinked( entity );

    assertPresent( r, type, id, entity );
    assertPresent( r, parentType, id, entity );
    assertNotPresent( r, type, "X" );
    assertNotPresent( r, parentType, "X" );

    // entity linked the first time it is "found" in repository
    assertLinked( entity );

    r.deregisterEntity( type, id );

    // entity is de-linked after de-registration
    assertInvalidated( entity );

    assertNotPresent( r, type, id );
    assertNotPresent( r, parentType, id );
    assertNotPresentInSet( r, type, entity );
  }

  @Test
  public void linkedAfterFindAll()
  {
    final EntityRepository r = new EntityRepositoryImpl();
    final Class<A> type = A.class;
    final String id = "A";
    final A entity = new A();

    r.registerEntity( type, id, entity );

    assertNotLinked( entity );

    final ArrayList<A> results = r.findAll( type );

    assertLinked( entity );

    assertTrue( results.contains( entity ) );
  }

  @Test
  public void findAllSorted()
  {
    final EntityRepository r = new EntityRepositoryImpl();

    final C e1 = new C( "A" );
    final C e2 = new C( "B" );
    final C e3 = new C( "C" );
    final C e4 = new C( "D" );
    r.registerEntity( C.class, 3, e3 );
    r.registerEntity( C.class, 2, e2 );
    r.registerEntity( C.class, 4, e4 );
    r.registerEntity( C.class, 1, e1 );

    final ArrayList<C> results = r.findAll( C.class, Comparator.comparing( C::getCode ) );
    assertEquals( results.size(), 4 );
    assertEquals( results.indexOf( e1 ), 0 );
    assertEquals( results.indexOf( e2 ), 1 );
    assertEquals( results.indexOf( e3 ), 2 );
    assertEquals( results.indexOf( e4 ), 3 );
  }

  @Test
  public void findAllIDs()
  {
    final EntityRepository r = new EntityRepositoryImpl();
    final Class<A> type = A.class;
    final String id = "A";

    r.registerEntity( type, id, new A() );

    assertTrue( r.findAllIDs( type ).contains( id ) );
  }

  @Test
  public void duplicateRegisterRaisesException()
  {
    final EntityRepository r = new EntityRepositoryImpl();
    final Class<A> type = A.class;
    final String id = "A";
    final A entity = new A();

    r.registerEntity( type, id, entity );

    try
    {
      r.registerEntity( type, id, entity );
    }
    catch ( final IllegalStateException e )
    {
      assertEquals( "Attempting to register duplicate entity with type '" + type.getName() + "' and id = '" + id + "'",
                    e.getMessage() );
      return;
    }
    fail( "Expected to raise an exception when re-registering an entity" );
  }

  @Test
  public void deregisterNonExistentRaisesException()
  {
    final EntityRepository r = new EntityRepositoryImpl();
    final Class<A> type = A.class;
    final String id = "A";

    try
    {
      r.deregisterEntity( type, id );
    }
    catch ( final IllegalStateException e )
    {
      assertEquals( "Attempting to de-register non existent entity with type '" + type.getName() +
                    "' and id = '" + id + "'", e.getMessage() );
      return;
    }
    fail( "Expected to raise an exception when de-registering an entity not present" );
  }

  @Test
  public void findByQuery()
  {
    final EntityRepository r = new EntityRepositoryImpl();

    final C e1 = new C( "A" );
    final C e2 = new C( "B" );
    final C e3 = new C( "C" );
    final C e4 = new C( "D" );
    r.registerEntity( C.class, 1, e1 );
    r.registerEntity( C.class, 2, e2 );
    r.registerEntity( C.class, 3, e3 );
    r.registerEntity( C.class, 4, e4 );

    assertEquals( r.findByQuery( C.class, e -> Objects.equals( e.getCode(), "B" ) ), e2 );
    assertEquals( r.findByQuery( C.class, e -> Objects.equals( e.getCode(), "D" ) ), e4 );
    assertEquals( r.findByQuery( C.class, e -> Objects.equals( e.getCode(), "X" ) ), null );
  }

  @Test
  public void findAllByQuery()
  {
    final EntityRepository r = new EntityRepositoryImpl();

    final C e1 = new C( "A" );
    final C e2 = new C( "B" );
    final C e3 = new C( "C" );
    final C e4 = new C( "D" );
    r.registerEntity( C.class, 3, e3 );
    r.registerEntity( C.class, 2, e2 );
    r.registerEntity( C.class, 4, e4 );
    r.registerEntity( C.class, 1, e1 );

    {
      final ArrayList<C> results = r.findAllByQuery( C.class, e -> Objects.equals( e.getCode(), "B" ) );
      assertEquals( results.size(), 1 );
      assertEquals( results.get( 0 ), e2 );
    }
    {
      final ArrayList<C> results = r.findAllByQuery( C.class, e -> !Objects.equals( e.getCode(), "B" ) );
      assertEquals( results.size(), 3 );
      assertTrue( results.contains( e1 ) );
      assertTrue( results.contains( e3 ) );
      assertTrue( results.contains( e4 ) );
    }

    {
      final ArrayList<C> results =
        r.findAllByQuery( C.class, e -> !Objects.equals( e.getCode(), "B" ), Comparator.comparing( C::getCode ) );
      assertEquals( results.size(), 3 );
      assertEquals( results.indexOf( e1 ), 0 );
      assertEquals( results.indexOf( e3 ), 1 );
      assertEquals( results.indexOf( e4 ), 2 );
    }
  }

  private void assertPresent( final EntityRepository r, final Class<?> type, final Object id, final Object entity )
  {
    assertCanFind( r, type, id, entity );
    assertCanGet( r, type, id, entity );
    assertPresentInSet( r, type, entity );
  }

  private void assertNotPresent( final EntityRepository r, final Class<?> type, final Object id )
  {
    assertCanNotFind( r, type, id );
    assertCanNotGet( r, type, id );
  }

  private void assertNotPresentInSet( final EntityRepository r, final Class<?> type, final Object entity )
  {
    assertFalse( r.findAll( type ).contains( entity ) );
  }

  private void assertPresentInSet( final EntityRepository r, final Class<?> type, final Object entity )
  {
    assertTrue( r.findAll( type ).contains( entity ) );
  }

  private void assertCanFind( final EntityRepository r, final Class<?> type, final Object id, final Object entity )
  {
    final Object actual = r.findByID( type, id );
    assertEquals( actual, entity );
    assertLinked( (B) actual );
  }

  private void assertCanGet( final EntityRepository r, final Class<?> type, final Object id, final Object entity )
  {
    final Object actual = r.getByID( type, id );
    assertEquals( actual, entity );
    assertLinked( (B) actual );
  }

  private void assertCanNotFind( final EntityRepository r, final Class<?> type, final Object id )
  {
    assertNull( r.findByID( type, id ) );
  }

  private void assertCanNotGet( final EntityRepository r, final Class<?> type, final Object id )
  {
    boolean failed;
    try
    {
      r.getByID( type, id );
      failed = false;
    }
    catch ( final NoSuchEntityException e )
    {
      assertEquals( e.getID(), id );
      assertEquals( e.getType(), type );
      failed = true;
    }
    if ( !failed )
    {
      fail( "Expected getByID to fail with exception for bad ID" );
    }
  }

  private static void assertNotLinked( final B entity )
  {
    assertFalse( entity._linked );
  }

  private static void assertInvalidated( final B entity )
  {
    assertTrue( entity._invalidated );
  }

  private static void assertLinked( final B entity )
  {
    assertFalse( entity._invalidated );
    assertTrue( entity._linked );
  }

  static class B
    implements Linkable
  {
    boolean _linked;
    boolean _invalidated;

    public final void link()
    {
      _linked = true;
    }

    @Override
    public boolean isLinked()
    {
      return _linked && isValid();
    }

    @Override
    public void invalidate()
    {
      assertFalse( _invalidated );
      _invalidated = true;
    }

    @Override
    public boolean isValid()
    {
      return !_invalidated;
    }
  }

  static class A
    extends B
  {
  }

  static class C
    extends B
  {
    private final String _code;

    public C( final String code )
    {
      _code = code;
    }

    public String getCode()
    {
      return _code;
    }
  }
}