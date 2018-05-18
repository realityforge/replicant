package replicant.spy;

import java.util.HashMap;
import org.testng.annotations.Test;
import replicant.AbstractReplicantTest;
import static org.testng.Assert.*;

public class MessageProcessFailureEventTest
  extends AbstractReplicantTest
{
  @Test
  public void basicOperation()
  {
    final MessageProcessFailureEvent event =
      new MessageProcessFailureEvent( G.class, new Error( "Some ERROR" ) );

    assertEquals( event.getSystemType(), G.class );

    final HashMap<String, Object> data = new HashMap<>();
    safeAction( () -> event.toMap( data ) );

    assertEquals( data.get( "type" ), "Connector.MessageProcessFailure" );
    assertEquals( data.get( "systemType" ), "G" );
    assertEquals( data.get( "message" ), "Some ERROR" );
    assertEquals( data.size(), 3 );
  }

  @Test
  public void basicOperation_ThrowableNoMessage()
  {
    final MessageProcessFailureEvent event =
      new MessageProcessFailureEvent( G.class, new NullPointerException() );

    assertEquals( event.getSystemType(), G.class );

    final HashMap<String, Object> data = new HashMap<>();
    safeAction( () -> event.toMap( data ) );

    assertEquals( data.get( "type" ), "Connector.MessageProcessFailure" );
    assertEquals( data.get( "systemType" ), "G" );
    assertEquals( data.get( "message" ), "java.lang.NullPointerException" );
    assertEquals( data.size(), 3 );
  }

  enum G
  {
    G1
  }
}
