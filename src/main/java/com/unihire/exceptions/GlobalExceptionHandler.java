package com.unihire.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestControllerAdvice(basePackages = "com.unihire.controllers")
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildErrorResponse(ex, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", request);
    }

    @ExceptionHandler(DuplicateApplicationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateApplication(DuplicateApplicationException ex, HttpServletRequest request) {
        return buildErrorResponse(ex, HttpStatus.CONFLICT, "DUPLICATE_APPLICATION", request);
    }

    @ExceptionHandler(UnauthorizedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedOperation(UnauthorizedOperationException ex, HttpServletRequest request) {
        return buildErrorResponse(ex, HttpStatus.FORBIDDEN, "UNAUTHORIZED_OPERATION", request);
    }

    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOtp(InvalidOtpException ex, HttpServletRequest request) {
        return buildErrorResponse(ex, HttpStatus.UNAUTHORIZED, "INVALID_OTP", request);
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(org.springframework.security.authentication.BadCredentialsException ex, HttpServletRequest request) {
        return buildErrorResponse(new Exception("Invalid email or password"), HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex, HttpServletRequest request) {
        return buildErrorResponse(ex, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", request);
    }

    @ExceptionHandler(JobExpiredException.class)
    public ResponseEntity<ErrorResponse> handleJobExpired(JobExpiredException ex, HttpServletRequest request) {
        return buildErrorResponse(ex, HttpStatus.GONE, "JOB_EXPIRED", request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return buildErrorResponse(ex, HttpStatus.BAD_REQUEST, "BAD_REQUEST", request);
    }

    @ExceptionHandler({org.springframework.web.multipart.MaxUploadSizeExceededException.class, org.springframework.web.multipart.MultipartException.class})
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(Exception ex, HttpServletRequest request) {
        return buildErrorResponse(new Exception("File size exceeds maximum permitted limit of 10 MB."), HttpStatus.BAD_REQUEST, "MAX_UPLOAD_SIZE_EXCEEDED", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return buildErrorResponse(new Exception(message), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", request);
    }
    
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {
        return buildErrorResponse(new Exception(ex.getReason()), HttpStatus.valueOf(ex.getStatusCode().value()), "HTTP_ERROR", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        logger.error("Unhandled exception occurred at {}: ", request.getRequestURI(), ex);
        return buildErrorResponse(new Exception("An unexpected error occurred"), HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(Exception ex, HttpStatus status, String code, HttpServletRequest request) {
        if (status.is4xxClientError()) {
            logger.warn("Client error at {} ({}): {}", request.getRequestURI(), status.value(), ex.getMessage());
        }
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code)
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
                
        return new ResponseEntity<>(errorResponse, status);
    }
}
