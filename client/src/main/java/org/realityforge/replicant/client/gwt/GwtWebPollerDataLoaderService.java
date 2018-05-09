package org.realityforge.replicant.client.gwt;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.user.client.Window;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.realityforge.gwt.webpoller.client.AbstractHttpRequestFactory;
import org.realityforge.gwt.webpoller.client.RequestFactory;
import org.realityforge.gwt.webpoller.client.TimerBasedWebPoller;
import org.realityforge.gwt.webpoller.client.WebPoller;
import org.realityforge.replicant.client.transport.CacheService;
import org.realityforge.replicant.client.transport.ClientSession;
import org.realityforge.replicant.client.transport.RequestEntry;
import org.realityforge.replicant.client.transport.SessionContext;
import org.realityforge.replicant.shared.transport.ReplicantContext;
import replicant.ReplicantRuntime;
import replicant.SafeProcedure;

public abstract class GwtWebPollerDataLoaderService
  extends GwtDataLoaderService
{
  protected class ReplicantRequestFactory
    extends AbstractHttpRequestFactory
  {
    @Override
    protected RequestBuilder getRequestBuilder()
    {
      return newSessionBasedInvocationBuilder( RequestBuilder.GET, getPollURL() );
    }
  }

  public GwtWebPollerDataLoaderService( @Nonnull final Class<?> systemType,
                                        @Nonnull final ReplicantRuntime replicantRuntime,
                                        @Nonnull final CacheService cacheService,
                                        @Nonnull final SessionContext sessionContext )
  {
    super( systemType, replicantRuntime, cacheService, sessionContext );
    createWebPoller();
    setupCloseHandler();
  }

  @Nonnull
  @Override
  protected WebPoller newWebPoller()
  {
    return new TimerBasedWebPoller();
  }

  protected void setupCloseHandler()
  {
    final Window.ClosingHandler handler = event -> disconnect();
    Window.addWindowClosingHandler( handler );
  }

  @Override
  protected void doConnect( @Nonnull final SafeProcedure action )
  {
    final Consumer<Response> onResponse =
      r -> onConnectResponse( r.getStatusCode(), r.getStatusText(), r::getText, action );
    sendRequest( RequestBuilder.POST, getBaseSessionURL(), onResponse, this::onConnectFailure );
  }

  @Override
  protected void doDisconnect( @Nonnull final SafeProcedure action )
  {
    final Consumer<Response> onResponse = r -> onDisconnectResponse( r.getStatusCode(), r.getStatusText(), action );
    final Consumer<Throwable> onError = t -> onDisconnectError( t, action );
    sendRequest( RequestBuilder.DELETE, getSessionURL(), onResponse, onError );
  }

  @Override
  protected void doSubscribe( @Nullable final ClientSession session,
                              @Nullable final RequestEntry request,
                              @Nullable final Object filterParameter,
                              @Nonnull final String channelURL,
                              @Nullable final String eTag,
                              @Nonnull final Runnable onSuccess,
                              @Nullable final Runnable onCacheValid,
                              @Nonnull final Consumer<Throwable> onError )
  {
    httpRequest( session,
                 request,
                 RequestBuilder.PUT,
                 channelURL,
                 eTag,
                 filterToString( filterParameter ),
                 onSuccess,
                 onCacheValid,
                 onError );
  }

  @Override
  protected void doUnsubscribe( @Nullable final ClientSession session,
                                @Nullable final RequestEntry request,
                                @Nonnull final String channelURL,
                                @Nonnull final Runnable onSuccess,
                                @Nonnull final Consumer<Throwable> onError )
  {
    httpRequest( session, request, RequestBuilder.DELETE, channelURL, null, null, onSuccess, null, onError );
  }

  private void httpRequest( @Nullable final ClientSession session,
                            @Nullable final RequestEntry request,
                            @Nonnull final RequestBuilder.Method method,
                            @Nonnull final String url,
                            @Nullable final String eTag,
                            @Nullable final String requestData,
                            @Nonnull final Runnable onSuccess,
                            @Nullable final Runnable onCacheValid,
                            @Nonnull final Consumer<Throwable> onError )
  {
    final ActionCallbackAdapter adapter =
      new ActionCallbackAdapter( onSuccess, onCacheValid, onError, request, session );
    final String requestID = null != request ? request.getRequestID() : null;
    final RequestBuilder rb = newRequestBuilder( method, url );
    if ( null != requestID )
    {
      rb.setHeader( ReplicantContext.REQUEST_ID_HEADER, requestID );
    }
    if ( null != eTag )
    {
      rb.setHeader( ReplicantContext.ETAG_HEADER, eTag );
    }
    try
    {
      rb.sendRequest( requestData, adapter );
    }
    catch ( final RequestException e )
    {
      adapter.onError( null, e );
    }
  }

  private void sendRequest( @Nonnull final RequestBuilder.Method method,
                            @Nonnull final String url,
                            @Nonnull final Consumer<Response> onResponse,
                            @Nonnull final Consumer<Throwable> onError )
  {
    final RequestBuilder rb = newRequestBuilder( method, url );
    try
    {
      rb.sendRequest( null, new RequestCallback()
      {
        @Override
        public void onResponseReceived( final Request request, final Response response )
        {
          onResponse.accept( response );
        }

        @Override
        public void onError( final Request request, final Throwable exception )
        {
          onError.accept( exception );
        }
      } );
    }
    catch ( final RequestException e )
    {
      onError.accept( e );
    }
  }

  @Nonnull
  @Override
  protected RequestFactory newRequestFactory()
  {
    return new ReplicantRequestFactory();
  }

  @Nonnull
  private RequestBuilder newSessionBasedInvocationBuilder( @Nonnull final RequestBuilder.Method method,
                                                           @Nonnull final String url )
  {
    final RequestBuilder rb = newRequestBuilder( method, url );
    rb.setHeader( ReplicantContext.SESSION_ID_HEADER, ensureSession().getSessionID() );
    return rb;
  }

  @Nonnull
  protected RequestBuilder newRequestBuilder( @Nonnull final RequestBuilder.Method method,
                                              @Nonnull final String url )
  {
    final RequestBuilder rb = new RequestBuilder( method, url );
    //Timeout 2 seconds after maximum poll
    rb.setTimeoutMillis( ( ReplicantContext.MAX_POLL_TIME_IN_SECONDS + 2 ) * 1000 );
    rb.setHeader( "Pragma", "no-cache" );
    final String authenticationToken = getSessionContext().getAuthenticationToken();
    if ( null != authenticationToken )
    {
      rb.setHeader( "Authorization", "Bearer " + authenticationToken );
    }
    return rb;
  }

  @Nonnull
  @Override
  protected String doFilterToString( @Nonnull final Object filterParameter )
  {
    return new JSONObject( (JavaScriptObject) filterParameter ).toString();
  }
}
