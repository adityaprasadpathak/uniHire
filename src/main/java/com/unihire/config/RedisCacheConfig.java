package com.unihire.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis cache and connection configuration for the application.
 *
 * <p>Supports both:
 * <ul>
 *   <li><b>Local non-TLS Redis:</b> Plain TCP for local instances (when no username/password is provided).</li>
 *   <li><b>Cloud TLS/SSL Redis:</b> Encrypted TLS connection (automatically enabled when username/password is provided).</li>
 * </ul>
 */
@Configuration
@EnableCaching
@Profile("!test")
public class RedisCacheConfig implements org.springframework.cache.annotation.CachingConfigurer {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RedisCacheConfig.class);

    /**
     * Configures a LettuceConnectionFactory that automatically uses TLS if username/password
     * is provided, and non-TLS (plain TCP) if no credentials are provided.
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.username:}") String username) {

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(host, port);
        boolean hasPassword = password != null && !password.isBlank();
        boolean hasUsername = username != null && !username.isBlank();

        if (hasPassword) {
            redisConfig.setPassword(RedisPassword.of(password));
        }
        if (hasUsername) {
            redisConfig.setUsername(username);
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfigBuilder =
                LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofSeconds(5));

        // Rule: If username and/or password is provided, use TLS; otherwise plain non-TLS
        boolean useSsl = hasPassword || hasUsername;

        if (useSsl) {
            logger.info("Configuring Redis connection WITH SSL/TLS (credentials provided) for host: {}:{}", host, port);
            clientConfigBuilder.useSsl();
        } else {
            logger.info("Configuring Redis connection WITHOUT SSL/TLS (no credentials - local non-TLS) for host: {}:{}", host, port);
        }

        return new LettuceConnectionFactory(redisConfig, clientConfigBuilder.build());
    }

    @Override
    public org.springframework.cache.interceptor.CacheErrorHandler errorHandler() {
        return new org.springframework.cache.interceptor.SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                logger.warn("Redis unavailable during cache GET for key '{}' in cache '{}'. Falling back to database: {}", key, cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache, Object key, Object value) {
                logger.warn("Redis unavailable during cache PUT for key '{}' in cache '{}': {}", key, cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                logger.warn("Redis unavailable during cache EVICT for key '{}' in cache '{}': {}", key, cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
                logger.warn("Redis unavailable during cache CLEAR for cache '{}': {}", cache.getName(), exception.getMessage());
            }
        };
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Base serialization config (shared by all caches)
        org.springframework.data.redis.serializer.RedisSerializer<Object> jsonSerializer = org.springframework.data.redis.serializer.RedisSerializer.json();
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> "v2::" + cacheName + "::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        // Per-cache TTL overrides
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Public job details — 30 minutes
        cacheConfigurations.put("publicJobs", base.entryTtl(Duration.ofMinutes(30)));

        // Candidate profile + sub-collections — 20 minutes
        cacheConfigurations.put("candidateProfiles", base.entryTtl(Duration.ofMinutes(20)));

        // Employer profile — 20 minutes
        cacheConfigurations.put("employerProfiles", base.entryTtl(Duration.ofMinutes(20)));

        // Dashboard snapshots — 10 minutes (volatile: contains live application/recommendation data)
        cacheConfigurations.put("dashboardMetrics", base.entryTtl(Duration.ofMinutes(10)));

        // Analytics — 5 minutes (frequently changing: views, applications, hires)
        cacheConfigurations.put("analytics", base.entryTtl(Duration.ofMinutes(5)));

        // Recommendation results — 15 minutes (evicted on profile/job updates)
        cacheConfigurations.put("recommendations", base.entryTtl(Duration.ofMinutes(15)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(60))) // fallback default
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
