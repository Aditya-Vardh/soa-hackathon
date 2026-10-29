package edu.soa.s09.t09.gatewayservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClerkJwtVerifier {
    private final JwtDecoder decoder;
    private final Set<String> learnerIds;
    private final Set<String> instructorIds;
    private final java.util.Map<String, String> userMappings;

    public ClerkJwtVerifier(
            @Value("${clerk.jwks-url:}") String jwksUrl,
            @Value("${clerk.issuer-url:}") String issuerUrl,
            @Value("${clerk.learner-ids:}") String configuredLearners,
            @Value("${clerk.instructor-ids:}") String configuredInstructors,
            @Value("${clerk.user-mappings:}") String configuredMappings) {
        this.decoder = createDecoder(jwksUrl, issuerUrl);
        this.learnerIds = ids(configuredLearners);
        this.instructorIds = ids(configuredInstructors);
        this.userMappings = mappings(configuredMappings);
    }

    public boolean isConfigured() {
        return decoder != null;
    }

    
public VerifiedUser verify(String token) {
    if (decoder == null) {
        throw new IllegalStateException("Clerk JWKS URL is not configured");
    }

    Jwt jwt = decoder.decode(token);
    String subject = jwt.getSubject();

    String mappedUser = userMappings.getOrDefault(subject, subject);

    String role = instructorIds.contains(subject) || instructorIds.contains(mappedUser)
            ? "ROLE_INSTRUCTOR"
            : learnerIds.contains(subject) || learnerIds.contains(mappedUser)
                    ? "ROLE_LEARNER"
                    : null;

    if (role == null) {
        throw new IllegalArgumentException("Clerk identity has no RetryLab role assignment");
    }

    return new VerifiedUser(mappedUser, role, jwt.getClaimAsString("name"));
}


    private static JwtDecoder createDecoder(String jwksUrl, String issuerUrl) {
        if (jwksUrl == null || jwksUrl.isBlank()) return null;
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUrl).build();
        OAuth2TokenValidator<Jwt> validator = issuerUrl == null || issuerUrl.isBlank()
                ? JwtValidators.createDefault()
                : JwtValidators.createDefaultWithIssuer(issuerUrl);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validator));
        return decoder;
    }

    private static Set<String> ids(String value) {
        return Arrays.stream(value == null ? new String[0] : value.split(","))
                .map(String::trim).filter(id -> !id.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    private static java.util.Map<String, String> mappings(String value) {
        return Arrays.stream(value == null ? new String[0] : value.split(","))
                .map(String::trim).filter(entry -> entry.contains("="))
                .map(entry -> entry.split("=", 2))
                .collect(Collectors.toUnmodifiableMap(pair -> pair[0].trim(), pair -> pair[1].trim(), (first, ignored) -> first));
    }

    public record VerifiedUser(String id, String role, String fullName) {}
}
