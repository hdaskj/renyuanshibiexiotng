package com.se2026.ai03.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/dev-login")
    public AuthService.LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username());
    }

    public record LoginRequest(@NotBlank String username) {
    }
}
