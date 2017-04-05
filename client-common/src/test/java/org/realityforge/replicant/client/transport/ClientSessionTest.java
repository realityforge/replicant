package org.realityforge.replicant.client.transport;

import org.realityforge.guiceyloops.shared.ValueUtil;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ClientSessionTest
{
  @Test
  public void basicRequestManagementWorkflow()
  {
    final ClientSession rm = new ClientSession( new TestDataLoadService(), ValueUtil.randomString() );
    final RequestEntry e = rm.newRequestRegistration( "Y", "X", true );
    assertEquals( e.isBulkLoad(), true );
    assertEquals( e.getRequestKey(), "Y" );
    assertEquals( e.getCacheKey(), "X" );

    assertEquals( rm.getRequest( e.getRequestID() ), e );
    assertEquals( rm.getRequests().get( e.getRequestID() ), e );
    assertEquals( rm.getRequest( "NotHere" + e.getRequestID() ), null );

    assertTrue( rm.removeRequest( e.getRequestID() ) );
    assertFalse( rm.removeRequest( e.getRequestID() ) );

    assertEquals( rm.getRequest( e.getRequestID() ), null );
  }
}
