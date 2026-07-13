package com.yowpainter.modules.admin.infrastructure.adapter.in.web;

import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.AdminRegisterRequest;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.AuthResponse;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.LoginRequest;
import com.yowpainter.modules.auth.application.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Admin Authentication", description = "Endpoints d'authentification réservés aux Administrateurs")
public class AdminAuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Connexion Administrateur")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.loginAdmin(request));
    }

    @PostMapping("/register")
    @Operation(summary = "Inscription d'un nouvel Administrateur (Restreint)")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AdminRegisterRequest request) {
        try {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(authService.registerAdmin(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(AuthResponse.builder()
                    .message(e.getMessage())
                    .build());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_IMPLEMENTED).body(AuthResponse.builder()
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AuthResponse.builder()
                    .message("Erreur interne: " + e.getMessage() + (e.getCause() != null ? " - Cause: " + e.getCause().getMessage() : ""))
                    .build());
        }
    }
}
