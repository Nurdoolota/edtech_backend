package com.lms.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void filter_noExistingRequestId_generatesUuid() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/me").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenAnswer(inv -> {
            ServerWebExchange ex = inv.getArgument(0);
            String id = ex.getRequest().getHeaders().getFirst(CorrelationIdFilter.REQUEST_ID_HEADER);
            assertThat(id).isNotBlank();
            return Mono.empty();
        });

        filter.filter(exchange, chain).block();
    }

    @Test
    void filter_existingRequestId_preservesIt() {
        String existingId = "my-correlation-id";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/me")
                .header(CorrelationIdFilter.REQUEST_ID_HEADER, existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenAnswer(inv -> {
            ServerWebExchange ex = inv.getArgument(0);
            String id = ex.getRequest().getHeaders().getFirst(CorrelationIdFilter.REQUEST_ID_HEADER);
            assertThat(id).isEqualTo(existingId);
            return Mono.empty();
        });

        filter.filter(exchange, chain).block();
    }

    @Test
    void filter_orderIsMinusThree() {
        assertThat(filter.getOrder()).isEqualTo(-3);
    }
}
