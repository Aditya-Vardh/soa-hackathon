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
    private final ClerkJwtVerifier clerkJwtVerifier;
    @org.springframework.beans.factory.annotation.Value("${security.gateway-secret:}")
    private String gatewaySecret;

    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/auth/login",
            "/auth/status",
            "/actuator"
    );

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
        this.clerkJwtVerifier = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JwtAuthFilter(JwtUtil jwtUtil, ClerkJwtVerifier clerkJwtVerifier) {
        this.jwtUtil = jwtUtil;
        this.clerkJwtVerifier = clerkJwtVerifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (request.getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        if (EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith)) {
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.substring(7).trim();
                    AuthenticatedUser user = validateUser(token);
                    ServerHttpRequest mutated = request.mutate()
                            .header("X-User-Id", user.id())
                            .header("X-User-Role", user.role())
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
            AuthenticatedUser user = validateUser(token);

            ServerHttpRequest mutated = request.mutate()
                    .headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Role");
                        headers.set("X-User-Id", user.id());
                        headers.set("X-User-Role", user.role());
                        if (gatewaySecret != null && !gatewaySecret.isBlank()) {
                            headers.set("X-Gateway-Secret", gatewaySecret);
                        }
                    })
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired JWT token: " + e.getMessage());
        }
    }

    private AuthenticatedUser validateUser(String token) {
        try {
            Claims claims = jwtUtil.validateAndGetClaims(token);
            if (claims != null && claims.getSubject() != null) {
                return new AuthenticatedUser(claims.getSubject(), claims.get("role", String.class));
            }
        } catch (Exception ignored) {
        }

        if (clerkJwtVerifier != null && clerkJwtVerifier.isConfigured()) {
            ClerkJwtVerifier.VerifiedUser user = clerkJwtVerifier.verify(token);
            return new AuthenticatedUser(user.id(), user.role());
        }

        throw new IllegalArgumentException("Invalid JWT signature or unrecognized token format");
    }

    private record AuthenticatedUser(String id, String role) {}

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
