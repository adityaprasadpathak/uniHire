package com.unihire.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.unihire.dto.*;
import com.unihire.exceptions.BadRequestException;
import com.unihire.exceptions.InvalidOtpException;
import com.unihire.exceptions.RateLimitExceededException;
import com.unihire.exceptions.ResourceNotFoundException;
import com.unihire.exceptions.UnauthorizedOperationException;
import com.unihire.models.*;
import com.unihire.repositories.CandidateProfileRepository;
import com.unihire.repositories.EmployerProfileRepository;
import com.unihire.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final EmployerProfileRepository employerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final SmsService smsService;
    private final StringRedisTemplate redisTemplate;

    @Value("${oauth2.google.client-id}")
    private String googleClientId;

    public AuthService(UserRepository userRepository,
                       CandidateProfileRepository candidateProfileRepository,
                       EmployerProfileRepository employerProfileRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       RefreshTokenService refreshTokenService,
                       SmsService smsService,
                       StringRedisTemplate redisTemplate) {
        this.userRepository = userRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.employerProfileRepository = employerProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
        this.smsService = smsService;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public AuthResponse registerCandidate(RegisterCandidateRequest request) {
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(Role.CANDIDATE)
                .build();
        userRepository.save(user);

        CandidateProfile profile = CandidateProfile.builder()
                .user(user)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .build();
        candidateProfileRepository.save(profile);

        return generateAuthResponse(user, true);
    }

    @Transactional
    public AuthResponse registerEmployer(RegisterEmployerRequest request) {
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.EMPLOYER)
                .build();
        userRepository.save(user);

        EmployerProfile profile = EmployerProfile.builder()
                .user(user)
                .companyName(request.getCompanyName())
                .website(request.getWebsite())
                .build();
        employerProfileRepository.save(profile);

        return generateAuthResponse(user, true);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return generateAuthResponse(user, false);
    }

    @Transactional
    public AuthResponse oauth2Login(OAuth2Request request) {
        if ("GOOGLE".equalsIgnoreCase(request.getProvider())) {
            return processGoogleOAuth(request.getIdToken());
        }
        // LinkedIn and others can be added here
        throw new BadRequestException("Unsupported provider: " + request.getProvider());
    }

    private AuthResponse processGoogleOAuth(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();
                String email = payload.getEmail();
                String subject = payload.getSubject(); // Google user ID

                Optional<User> optionalUser = userRepository.findByEmail(email);
                boolean isNewUser = false;
                User user;

                if (optionalUser.isPresent()) {
                    user = optionalUser.get();
                } else {
                    isNewUser = true;
                    user = User.builder()
                            .email(email)
                            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .role(Role.CANDIDATE) // Default to candidate for OAuth
                            .provider("GOOGLE")
                            .providerId(subject)
                            .build();
                    userRepository.save(user);
                }

                return generateAuthResponse(user, isNewUser);
            } else {
                throw new UnauthorizedOperationException("Invalid Google ID token");
            }
        } catch (Exception e) {
            throw new UnauthorizedOperationException("Failed to verify Google token: " + e.getMessage());
        }
    }

    public void sendOtp(SendOtpRequest request) {
        String phone = request.getPhone();
        String redisKey = "OTP:" + phone;
        String rateLimitKey = "OTP_RATE:" + phone;

        Long attempts = redisTemplate.opsForValue().increment(rateLimitKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(rateLimitKey, 1, TimeUnit.HOURS);
        }
        if (attempts != null && attempts > 5) {
            throw new RateLimitExceededException("Rate limit exceeded for OTP requests.");
        }

        String otp = String.format("%06d", new Random().nextInt(999999));
        
        // Save to redis with 5 minute expiration
        redisTemplate.opsForValue().set(redisKey, otp, 5, TimeUnit.MINUTES);
        
        smsService.sendOtp(phone, otp);
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        String phone = request.getPhone();
        String redisKey = "OTP:" + phone;
        
        String storedOtp = redisTemplate.opsForValue().get(redisKey);
        
        if (storedOtp == null || !storedOtp.equals(request.getOtp())) {
            throw new InvalidOtpException("Invalid or expired OTP");
        }

        redisTemplate.delete(redisKey); // Mark as used

        Optional<User> optionalUser = userRepository.findByEmail(phone + "@otp.local"); // Dummy email since email is required
        boolean isNewUser = false;
        User user;

        if (optionalUser.isPresent()) {
            user = optionalUser.get();
        } else {
            isNewUser = true;
            user = User.builder()
                    .email(phone + "@otp.local") 
                    .phone(phone)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(Role.CANDIDATE) // Default to candidate
                    .build();
            userRepository.save(user);
        }

        return generateAuthResponse(user, isNewUser);
    }

    public AuthResponse refreshToken(TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String token = jwtService.generateToken(user);
                    return AuthResponse.builder()
                            .accessToken(token)
                            .refreshToken(requestRefreshToken)
                            .role(user.getRole().name())
                            .email(user.getEmail())
                            .build();
                })
                .orElseThrow(() -> new UnauthorizedOperationException("Refresh token is not in database!"));
    }

    private AuthResponse generateAuthResponse(User user, boolean isNewUser) {
        String jwtToken = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());
        
        return AuthResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken.getToken())
                .role(user.getRole().name())
                .email(user.getEmail())
                .isNewUser(isNewUser)
                .build();
    }
}
