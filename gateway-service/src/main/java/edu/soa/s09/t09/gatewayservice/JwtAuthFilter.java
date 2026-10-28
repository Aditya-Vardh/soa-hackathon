package edu.soa.s09.t09.gatewayservice;

import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {
    private final JwtUtil jwtUtil;

    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/auth/login",
            "/auth/status",
            "/actuator"
    );

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Allow CORS pre-flight
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // Allow seed endpoint for demo / testing convenience
        if (path.contains("/seed") || EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith)) {
            // Still parse token if present to populate headers
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.substring(7).trim();
                    Claims claims = jwtUtil.validateAndGetClaims(token);
                    ServerHttpRequest mutated = request.mutate()
                            .header("X-User-Id", claims.getSubject())
                            .header("X-User-Role", claims.get("role", String.class))
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                } catch (Exception ignored) {
                }
            }
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7).trim();
        try {
            Claims claims = jwtUtil.validateAndGetClaims(token);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);

            ServerHttpRequest mutated = request.mutate()
                    .header("X-User-Id", username)
                    .header("X-User-Role", role)
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired JWT token: " + e.getMessage());
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":" + status.value() + ",\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}

