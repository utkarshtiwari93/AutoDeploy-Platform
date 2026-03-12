package com.utkarsh.in.DeploymentPlatform.controller;

import com.utkarsh.in.DeploymentPlatform.dto.request.LoginRequest;
import com.utkarsh.in.DeploymentPlatform.dto.request.RegisterRequest;
import com.utkarsh.in.DeploymentPlatform.dto.response.AuthResponse;
import com.utkarsh.in.DeploymentPlatform.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}