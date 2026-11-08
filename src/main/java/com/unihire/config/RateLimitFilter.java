package com.unihire.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * A global rate-limiting filter that uses Redis to track requests per IP.
 * Restricts clients to a maximum of 100 requests per minute.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private static final int MAX_REQUESTS_PER_MINUTE = 100;

    public RateLimitFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/health") || path.startsWith("/api/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String ipAddress = request.getRemoteAddr();
        
        // Use X-Forwarded-For if behind a proxy
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            ipAddress = xForwardedFor.split(",")[0].trim();
        }

        String key = "rate_limit:ip:" + ipAddress;
        
        try {
            Long currentRequests = redisTemplate.opsForValue().increment(key);
            
            if (currentRequests != null && currentRequests == 1) {
                redisTemplate.expire(key, Duration.ofMinutes(1));
            }

            if (currentRequests != null && currentRequests > MAX_REQUESTS_PER_MINUTE) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("Too Many Requests. Please try again later.");
                return;
            }
        } catch (Exception e) {
            logger.warn("Rate limiting Redis operation failed: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
