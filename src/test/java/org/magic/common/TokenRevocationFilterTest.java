package org.magic.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class TokenRevocationFilterTest {

    @Mock
    TokenRevocationService revocationService;

    @Mock
    ContainerRequestContext requestContext;

    TokenRevocationFilter filter;

    @BeforeEach
    void setup() {
        filter = new TokenRevocationFilter(revocationService);
    }

    @Test
    void shouldAbortWith401WhenTokenRevoked() throws Exception {
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer revoked-jwt");
        when(revocationService.isRevoked("revoked-jwt")).thenReturn(true);

        Response response = filter.filter(requestContext);

        assertNotNull(response);
    }

    @Test
    void shouldPassWhenTokenNotRevoked() throws Exception {
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer valid-jwt");
        when(revocationService.isRevoked("valid-jwt")).thenReturn(false);

        Response response = filter.filter(requestContext);

        assertNull(response);
    }

    @Test
    void shouldPassWhenNoAuthorizationHeader() throws Exception {
        Response response = filter.filter(requestContext);

        assertNull(response);
        verifyNoInteractions(revocationService);
    }

    @Test
    void shouldPassWhenHeaderIsNotBearer() throws Exception {
        when(requestContext.getHeaderString("Authorization")).thenReturn("Basic abc123");

        Response response = filter.filter(requestContext);

        assertNull(response);
        verifyNoInteractions(revocationService);
    }
}
