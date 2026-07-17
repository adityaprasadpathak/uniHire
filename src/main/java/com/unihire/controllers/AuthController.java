package com.unihire.controllers;

import com.unihire.dto.*;
import com.unihire.services.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register/candidate")
    public ResponseEntity<AuthResponse> registerCandidate(@RequestBody RegisterCandidateRequest request) {
        return ResponseEntity.ok(authService.registerCandidate(request));
    }

    @PostMapping("/register/employer")
    public ResponseEntity<AuthResponse> registerEmployer(@RequestBody RegisterEmployerRequest request) {
        return ResponseEntity.ok(authService.registerEmployer(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/oauth/{provider}")
    public ResponseEntity<AuthResponse> oauth2Login(
            @PathVariable String provider,
            @RequestBody OAuth2Request request) {
        // provider path variable allows /oauth/google, /oauth/linkedin
        request.setProvider(provider.toUpperCase());
        return ResponseEntity.ok(authService.oauth2Login(request));
    }

    @PostMapping("/otp/send")
    public ResponseEntity<Map<String, Boolean>> sendOtp(@RequestBody SendOtpRequest request) {
        authService.sendOtp(request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        // Since JWT is stateless, the frontend simply drops the token.
        // For refresh tokens, we would ideally delete the refresh token from the database here.
        // Implementation can take the refresh token in the body and delete it.
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
