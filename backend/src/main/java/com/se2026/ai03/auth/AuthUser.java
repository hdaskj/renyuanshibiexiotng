package com.se2026.ai03.auth;

public record AuthUser(String id, String displayName, String role) {
    public boolean isOwner() {
        return "OWNER".equals(role);
    }
}
