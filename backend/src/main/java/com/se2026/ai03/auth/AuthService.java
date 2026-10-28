package com.se2026.ai03.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private record Session(AuthUser user, Instant expiresAt) {
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public LoginResponse login(String username) {
        AuthUser user = switch (username) {
            case "participant" -> new AuthUser("u-participant", "林岚", "PARTICIPANT");
            case "owner" -> new AuthUser("u-owner", "林澜", "OWNER");
            case "admin" -> new AuthUser("u-admin", "系统管理员", "ADMIN");
            default -> throw new IllegalArgumentException("Unknown development user");
        };
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(8));
        sessions.put(token, new Session(user, expiresAt));
        return new LoginResponse(token, user, expiresAt.toString());
    }

    public AuthUser authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AuthenticationRequiredException();
        }
        String token = authorization.substring("Bearer ".length()).trim();
        Session session = sessions.get(token);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            throw new AuthenticationRequiredException();
        }
        return session.user();
    }

    public record LoginResponse(String token, AuthUser user, String expiresAt) {
    }

    public static class AuthenticationRequiredException extends RuntimeException {
        public AuthenticationRequiredException() {
            super("A valid development bearer token is required");
        }
    }
}
