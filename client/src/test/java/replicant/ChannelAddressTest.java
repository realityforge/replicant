package replicant;

import org.realityforge.guiceyloops.shared.ValueUtil;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ChannelAddressTest
  extends AbstractReplicantTest
{
  @Test
  public void construct()
  {
    final ChannelAddress address = new ChannelAddress( 2, 4, 1 );

    assertEquals( address.getSystemId(), 2 );
    assertEquals( address.getChannelId(), 4 );
    assertEquals( address.getId(), (Integer) 1 );
  }

  @SuppressWarnings( "EqualsWithItself" )
  @Test
  public void testEquals()
  {
    final ChannelAddress address = new ChannelAddress( 1, 2, 1 );
    final ChannelAddress address2 = new ChannelAddress( 1, 2, 2 );
    final ChannelAddress address3 = new ChannelAddress( 1, 1 );

    assertEquals( address.equals( address ), true );
    assertEquals( address.equals( new Object() ), false );
    assertEquals( address.equals( address2 ), false );
    assertEquals( address.equals( address3 ), false );
  }

  @Test
  public void toStringTest()
  {
    final ChannelAddress address = new ChannelAddress( 1, 2, 1 );
    assertEquals( address.toString(), "1.2.1" );
  }

  @Test
  public void toStringTest_NamingDisabled()
  {
    ReplicantTestUtil.disableNames();
    final ChannelAddress address =
      new ChannelAddress( ValueUtil.randomInt(), ValueUtil.randomInt(), ValueUtil.randomInt() );
    assertEquals( address.toString(), "replicant.ChannelAddress@" + Integer.toHexString( address.hashCode() ) );
  }

  @Test
  public void getName_NamingDisabled()
  {
    ReplicantTestUtil.disableNames();
    final ChannelAddress address =
      new ChannelAddress( ValueUtil.randomInt(), ValueUtil.randomInt(), ValueUtil.randomInt() );
    final IllegalStateException exception = expectThrows( IllegalStateException.class, address::getName );
    assertEquals( exception.getMessage(),
                  "Replicant-0042: ChannelAddress.getName() invoked when Replicant.areNamesEnabled() is false" );
  }

  @SuppressWarnings( "EqualsWithItself" )
  @Test
  public void compareTo()
  {
    final ChannelAddress address1 = new ChannelAddress( 1, 1 );
    final ChannelAddress address2 = new ChannelAddress( 1, 2, 1 );

    assertEquals( address1.compareTo( address1 ), 0 );
    assertEquals( address1.compareTo( address2 ), -1 );
    assertEquals( address2.compareTo( address1 ), 1 );
    assertEquals( address2.compareTo( address2 ), 0 );
  }
}
