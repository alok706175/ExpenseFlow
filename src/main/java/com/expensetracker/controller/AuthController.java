package com.expensetracker.controller;

import com.expensetracker.dto.AuthRequestDTO;
import com.expensetracker.dto.AuthResponseDTO;
import com.expensetracker.dto.RegisterRequestDTO;
import com.expensetracker.dto.UserProfileDTO;
import com.expensetracker.entity.User;
import com.expensetracker.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication & User Management", description = "Endpoints for user registration, JWT login, and profile preferences")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user account", description = "Creates account, hashes password with BCrypt, and returns a JWT token")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterRequestDTO registerDTO) {
        AuthResponseDTO response = authService.register(registerDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Validates credentials and returns JWT bearer token")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody AuthRequestDTO loginDTO) {
        return ResponseEntity.ok(authService.login(loginDTO));
    }

    @GetMapping("/profile")
    @Operation(summary = "Get current user profile & currency", description = "Retrieves profile and currency for authenticated user")
    public ResponseEntity<UserProfileDTO> getProfile() {
        User user = authService.getCurrentAuthenticatedUser();
        return ResponseEntity.ok(authService.getProfile(user.getId()));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update user profile and currency preference", description = "Updates display name and currency (INR, USD, EUR, etc.)")
    public ResponseEntity<UserProfileDTO> updateProfile(@Valid @RequestBody UserProfileDTO profileDTO) {
        User user = authService.getCurrentAuthenticatedUser();
        return ResponseEntity.ok(authService.updateProfile(user.getId(), profileDTO));
    }
}
