package org.realityforge.replicant.client.transport;

import javax.annotation.Nullable;
import org.realityforge.guiceyloops.shared.ValueUtil;
import org.testng.annotations.Test;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class SessionContextTest
{
  @Test
  public void basicOperation()
  {
    final String key = ValueUtil.randomString();
    final SessionContext sessionContext = new SessionContext( key );
    assertEquals( sessionContext.getKey(), key );

    //session
    {
      final ClientSession session = new ClientSession( mock( DataLoaderService.class ), ValueUtil.randomString() );
      assertEquals( sessionContext.getSession(), null );
      sessionContext.setSession( session );
      assertEquals( sessionContext.getSession(), session );
      sessionContext.setSession( null );
      assertEquals( sessionContext.getSession(), null );
    }

    //authToken
    {
      final String authToken = ValueUtil.randomString();
      assertEquals( sessionContext.getAuthenticationToken(), null );
      sessionContext.setAuthenticationToken( authToken );
      assertEquals( sessionContext.getAuthenticationToken(), authToken );
      sessionContext.setAuthenticationToken( null );
      assertEquals( sessionContext.getAuthenticationToken(), null );
    }
  }

  @Test
  public void request_noSession()
  {
    final String key = ValueUtil.randomString();
    final String requestKey = ValueUtil.randomString();
    final String cacheKey = ValueUtil.randomString();
    final RequestAction action = mock( RequestAction.class );

    final SessionContext sessionContext = new SessionContext( key );

    sessionContext.request( requestKey, cacheKey, action );

    verify( action ).invokeReqest( null, null );
  }

  @Test
  public void request_sessionPresent()
  {
    final String key = ValueUtil.randomString();
    final String requestKey = ValueUtil.randomString();
    final String cacheKey = ValueUtil.randomString();
    final TestRequestAction action = new TestRequestAction();
    final ClientSession session = new ClientSession( mock( DataLoaderService.class ), ValueUtil.randomString() );

    final SessionContext sessionContext = new SessionContext( key );
    sessionContext.setSession( session );

    sessionContext.request( requestKey, cacheKey, action );

    assertEquals( action._session, session );
    assertNotNull( action._request );
    assertEquals( action._request.getRequestKey(), requestKey );
    assertEquals( action._request.getCacheKey(), cacheKey );
  }

  static class TestRequestAction
    implements RequestAction
  {
    private ClientSession _session;
    private RequestEntry _request;

    @Override
    public void invokeReqest( @Nullable final ClientSession session, @Nullable final RequestEntry request )
    {
      _session = session;
      _request = request;
    }
  }
}