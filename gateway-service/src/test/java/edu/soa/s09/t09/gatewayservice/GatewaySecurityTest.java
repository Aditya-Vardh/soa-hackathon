package edu.soa.s09.t09.gatewayservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySecurityTest {
    private JwtUtil jwtUtil;
    private JwtAuthFilter filter;
    private AtomicBoolean chainCalled;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        filter = new JwtAuthFilter(jwtUtil);
        chainCalled = new AtomicBoolean(false);
        chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };
    }

    @Test
    void publicPathsPassWithoutToken() {
        MockServerHttpRequest req = MockServerHttpRequest.get("/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        filter.filter(exchange, chain).block();

        assertThat(chainCalled.get()).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // Not rejected
    }

    @Test
    void protectedPathWithoutTokenIsRejectedWith401() {
        MockServerHttpRequest req = MockServerHttpRequest.get("/entitlements/learner1").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        filter.filter(exchange, chain).block();

        assertThat(chainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
