package com.unihire.controllers;

import com.unihire.dto.*;
import com.unihire.services.auth.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import java.util.Map;

/**
 * REST controller for handling user authentication and authorization endpoints.
 * Provides APIs for registration, login, OAuth2 login, OTP generation/verification,
 * token refresh, password resets, and user logout.
 */
@Tag(name = "Authentication", description = "User registration, login, JWT token refresh, and OTP operations")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * Constructs a new {@code AuthController} with the specified {@link AuthService}.
     *
     * @param authService service handling core authentication logic and user management
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new candidate account in the system.
     *
     * @param request DTO containing candidate registration details (e.g. name, email, password)
     * @return {@link ResponseEntity} containing authentication response token and user info
     */
    @PostMapping("/register/candidate")
    public ResponseEntity<AuthResponse> registerCandidate(@RequestBody RegisterCandidateRequest request) {
        return ResponseEntity.ok(authService.registerCandidate(request));
    }

    /**
     * Registers a new employer account in the system.
     *
     * @param request DTO containing employer registration details (e.g. company name, email, password)
     * @return {@link ResponseEntity} containing authentication response token and user info
     */
    @PostMapping("/register/employer")
    public ResponseEntity<AuthResponse> registerEmployer(@RequestBody RegisterEmployerRequest request) {
        return ResponseEntity.ok(authService.registerEmployer(request));
    }

    /**
     * Authenticates a user with email/password credentials.
     *
     * @param request DTO containing login credentials (email and password)
     * @return {@link ResponseEntity} containing JWT access and refresh tokens upon successful authentication
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Authenticates or registers a user via a third-party OAuth2 provider.
     *
     * @param provider OAuth2 provider identifier from the path (e.g. "google", "linkedin")
     * @param request  DTO containing OAuth2 token/authorization code details
     * @return {@link ResponseEntity} containing authentication response token and user info
     */
    @PostMapping("/oauth/{provider}")
    public ResponseEntity<AuthResponse> oauth2Login(
            @PathVariable String provider,
            @RequestBody OAuth2Request request) {
        // provider path variable allows /oauth/google, /oauth/linkedin
        request.setProvider(provider.toUpperCase());
        return ResponseEntity.ok(authService.oauth2Login(request));
    }

    /**
     * Triggers sending a One-Time Password (OTP) code to the specified email or phone number.
     *
     * @param request DTO containing contact destination for OTP delivery
     * @return {@link ResponseEntity} containing a map indicating operational success
     */
    @PostMapping("/otp/send")
    public ResponseEntity<Map<String, Boolean>> sendOtp(@RequestBody SendOtpRequest request) {
        authService.sendOtp(request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Verifies a submitted One-Time Password (OTP) for account authentication or validation.
     *
     * @param request DTO containing recipient contact identifier and candidate OTP code
     * @return {@link ResponseEntity} containing authentication response upon successful OTP verification
     */
    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    /**
     * Issues a new JWT access token using a valid refresh token.
     *
     * @param request DTO containing the valid refresh token payload
     * @return {@link ResponseEntity} containing the updated authentication tokens
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    /**
     * Logs out the current user session by acknowledging token release.
     *
     * @return {@link ResponseEntity} containing confirmation message of successful logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        // Since JWT is stateless, the frontend simply drops the token.
        // For refresh tokens, we would ideally delete the refresh token from the database here.
        // Implementation can take the refresh token in the body and delete it.
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Resets a user's password using a valid reset request payload.
     *
     * @param request DTO containing password reset token/verification details and new password
     * @return {@link ResponseEntity} containing confirmation message of successful password reset
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }
}
