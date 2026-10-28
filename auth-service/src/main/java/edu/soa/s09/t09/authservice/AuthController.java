package edu.soa.s09.t09.authservice;

import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @PostConstruct
    public void seedInitialUsers() {
        if (userRepository.count() == 0) {
            userRepository.save(new User("learner1", "password", "ROLE_LEARNER", "Alex Mercer"));
            userRepository.save(new User("learner2", "password", "ROLE_LEARNER", "Sam Taylor"));
            userRepository.save(new User("instructor1", "password", "ROLE_INSTRUCTOR", "Dr. Sarah Chen"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) Map<String, String> body,
                                                     @RequestParam(name = "username", required = false) String paramUsername,
                                                     @RequestParam(name = "password", required = false) String paramPassword) {
        String username = (body != null && body.containsKey("username")) ? body.get("username") : paramUsername;
        String password = (body != null && body.containsKey("password")) ? body.get("password") : paramPassword;

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username and password required");
        }

        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (!user.getPassword().equals(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole(), user.getFullName());

        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("username", user.getUsername());
        resp.put("role", user.getRole());
        resp.put("fullName", user.getFullName());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7).trim();
        try {
            Claims claims = jwtUtil.validateAndGetClaims(token);
            Map<String, Object> resp = new HashMap<>();
            resp.put("username", claims.getSubject());
            resp.put("role", claims.get("role", String.class));
            resp.put("fullName", claims.get("fullName", String.class));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired JWT token");
        }
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> res = new HashMap<>();
        res.put("status", "UP");
        res.put("message", "Auth service operational with signed JWT security.");
        res.put("supportedRoles", List.of("ROLE_LEARNER", "ROLE_INSTRUCTOR"));
        return res;
    }

    @GetMapping("/users")
    public List<Map<String, String>> listUsers() {
        return userRepository.findAll().stream()
                .map(u -> Map.of("username", u.getUsername(), "role", u.getRole(), "fullName", u.getFullName()))
                .toList();
    }
}
