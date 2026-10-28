package edu.soa.s09.t09.authservice;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    private UserRepository userRepository;
    private JwtUtil jwtUtil;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtUtil = new JwtUtil();
        authController = new AuthController(userRepository, jwtUtil);
    }

    @Test
    void loginSuccessReturnsValidSignedJwt() {
        User user = new User("learner1", "password", "ROLE_LEARNER", "Alex Mercer");
        when(userRepository.findByUsername("learner1")).thenReturn(Optional.of(user));

        ResponseEntity<Map<String, Object>> response = authController.login(
                Map.of("username", "learner1", "password", "password"), null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("username")).isEqualTo("learner1");
        assertThat(body.get("role")).isEqualTo("ROLE_LEARNER");

        String token = (String) body.get("token");
        Claims claims = jwtUtil.validateAndGetClaims(token);
        assertThat(claims.getSubject()).isEqualTo("learner1");
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_LEARNER");
    }

    @Test
    void loginInvalidPasswordThrowsUnauthorized() {
        User user = new User("learner1", "password", "ROLE_LEARNER", "Alex Mercer");
        when(userRepository.findByUsername("learner1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authController.login(
                Map.of("username", "learner1", "password", "wrongpassword"), null, null))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.UNAUTHORIZED);
    }
}

