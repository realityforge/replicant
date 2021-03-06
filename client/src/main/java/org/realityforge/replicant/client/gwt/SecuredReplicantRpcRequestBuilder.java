package org.realityforge.replicant.client.gwt;

import com.google.gwt.http.client.RequestBuilder;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.realityforge.gwt.keycloak.Keycloak;

public final class SecuredReplicantRpcRequestBuilder
  extends ReplicantRpcRequestBuilder
{
  @Nonnull
  private final Keycloak _keycloak;

  public SecuredReplicantRpcRequestBuilder( @Nonnull final String baseUrl,
                                            final int schemaId,
                                            @Nonnull final Keycloak keycloak )
  {
    super( baseUrl, schemaId );
    _keycloak = Objects.requireNonNull( keycloak );
  }

  @Override
  protected void doFinish( final RequestBuilder rb )
  {
    final String token = _keycloak.getToken();
    if( null == token )
    {
      throw new IllegalStateException( "Missing token" );
    }
    else
    {
      rb.setHeader( "Authorization", "Bearer " + _keycloak.getToken() );
      super.doFinish( rb );
    }
  }
}
