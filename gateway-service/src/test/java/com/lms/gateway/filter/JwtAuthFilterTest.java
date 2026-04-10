package com.lms.gateway.filter;

import com.lms.gateway.config.JwtProperties;
import com.lms.gateway.security.JwtVerifier;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hmac-sha256-at-least-32-bytes";

    private JwtAuthFilter filter;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        JwtVerifier verifier = new JwtVerifier(props);
        filter = new JwtAuthFilter(verifier);
        secretKey = JwtVerifier.resolveKey(SECRET);
    }

    // --- Public paths bypass JWT ---

    @Test
    void filter_publicPath_register_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/register").build();
        assertPublicPathPassesThrough(request);
    }

    @Test
    void filter_publicPath_login_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login").build();
        assertPublicPathPassesThrough(request);
    }

    @Test
    void filter_publicPath_refresh_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/refresh").build();
        assertPublicPathPassesThrough(request);
    }

    @Test
    void filter_actuator_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        assertPublicPathPassesThrough(request);
    }

    // --- Protected paths with valid token ---

    @Test
    void filter_validToken_injectsXUserIdAndRole() {
        String token = buildAccessToken(99L, "TEACHER");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/content/courses")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenAnswer(inv -> {
            ServerWebExchange ex = inv.getArgument(0);
            assertThat(ex.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("99");
            assertThat(ex.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("TEACHER");
            return Mono.empty();
        });

        filter.filter(exchange, chain).block();
        verify(chain).filter(any());
    }

    // --- Protected paths with missing/invalid token ---

    @Test
    void filter_missingAuthHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/content/courses").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_malformedAuthHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/content/courses")
                .header("Authorization", "Basic abc123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_expiredToken_returns401() {
        String token = Jwts.builder()
                .subject("1")
                .claim("role", "STUDENT")
                .claim("typ", "access")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(1)))
                .signWith(secretKey)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/learning/tasks")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_refreshTokenUsedAsAccess_returns401() {
        String token = Jwts.builder()
                .subject("1")
                .claim("typ", "refresh")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(secretKey)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/learning/tasks")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_orderIsMinusOne() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }

    // --- Helpers ---

    private void assertPublicPathPassesThrough(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private String buildAccessToken(Long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("typ", "access")
                .claim("email", "user@example.com")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(secretKey)
                .compact();
    }
}
