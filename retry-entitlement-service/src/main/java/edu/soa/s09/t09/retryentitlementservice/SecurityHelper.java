package edu.soa.s09.t09.retryentitlementservice;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;

@Component
public class SecurityHelper {
    public static final String SECRET = "soa-s24-s09-t09-retrylab-jwt-super-secret-key-for-token-signing-32bytes";
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    @Value("${security.gateway-secret:}")
    private String gatewaySecret;

    public static class UserContext {
        public final String userId;
        public final String role;

        public UserContext(String userId, String role) {
            this.userId = userId;
            this.role = role;
        }

        public boolean isInstructor() {
            return "ROLE_INSTRUCTOR".equalsIgnoreCase(role);
        }

        public boolean isLearner() {
            return "ROLE_LEARNER".equalsIgnoreCase(role);
        }
    }

    public UserContext getCaller(HttpServletRequest request) {
        String gatewayUserId = request.getHeader("X-User-Id");
        String gatewayRole = request.getHeader("X-User-Role");
        if (gatewaySecret != null && !gatewaySecret.isBlank() && !gatewaySecret.equals(request.getHeader("X-Gateway-Secret"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing trusted gateway authentication");
        }

        if (gatewayUserId != null && !gatewayUserId.isBlank()) {
            return new UserContext(gatewayUserId.trim(), gatewayRole != null ? gatewayRole.trim() : "ROLE_LEARNER");
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7).trim();
                Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
                return new UserContext(claims.getSubject(), claims.get("role", String.class));
            } catch (Exception ignored) {
            }
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    public void verifyLearnerAccess(UserContext caller, String targetLearnerId) {
        if (caller == null || caller.userId == null) {
            return;
        }
        if (caller.isInstructor()) {
            return;
        }
        if (caller.isLearner() && !caller.userId.equals(targetLearnerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: Learner cannot access another learner's records");
        }
    }
}
