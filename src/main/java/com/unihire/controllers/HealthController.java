package com.unihire.controllers;

import com.unihire.dto.HealthResponse;
import com.unihire.dto.ServiceHealthDetail;
import com.unihire.services.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Controller providing liveness and detailed parallel dependency health checks.
 */
@RestController
@Tag(name = "Health Check", description = "Endpoints for server liveness and parallel dependency health checks")
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final OpenSearchClient openSearchClient;
    private final StorageService storageService;
    private final TaskExecutor taskExecutor;

    public HealthController(DataSource dataSource,
                            StringRedisTemplate redisTemplate,
                            @Autowired(required = false) OpenSearchClient openSearchClient,
                            @Autowired(required = false) StorageService storageService,
                            @Qualifier("taskExecutor") TaskExecutor taskExecutor) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.openSearchClient = openSearchClient;
        this.storageService = storageService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Simple liveness probe.
     * Returns HTTP 200 OK with {"status": "UP"} if the server is up.
     */
    @Operation(summary = "Simple liveness health check", description = "Returns HTTP 200 with status UP if the application is running.")
    @GetMapping({"/health", "/api/health"})
    public ResponseEntity<Map<String, String>> getSimpleHealth() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    /**
     * Extensive health check.
     * Checks database, redis, opensearch, and minio/storage connectivity in parallel.
     */
    @Operation(summary = "Extensive dependency health check", description = "Concurrently checks PostgreSQL, Redis, OpenSearch, and MinIO/Storage connectivity.")
    @GetMapping({"/health/extensive", "/api/health/extensive"})
    public ResponseEntity<HealthResponse> getExtensiveHealth() {
        long startTime = System.currentTimeMillis();

        CompletableFuture<ServiceHealthDetail> dbFuture = CompletableFuture.supplyAsync(
                this::checkDatabaseHealth, taskExecutor);

        CompletableFuture<ServiceHealthDetail> redisFuture = CompletableFuture.supplyAsync(
                this::checkRedisHealth, taskExecutor);

        CompletableFuture<ServiceHealthDetail> openSearchFuture = CompletableFuture.supplyAsync(
                this::checkOpenSearchHealth, taskExecutor);

        CompletableFuture<ServiceHealthDetail> storageFuture = CompletableFuture.supplyAsync(
                this::checkStorageHealth, taskExecutor);

        CompletableFuture.allOf(dbFuture, redisFuture, openSearchFuture, storageFuture).join();

        ServiceHealthDetail dbStatus = dbFuture.join();
        ServiceHealthDetail redisStatus = redisFuture.join();
        ServiceHealthDetail openSearchStatus = openSearchFuture.join();
        ServiceHealthDetail storageStatus = storageFuture.join();

        Map<String, ServiceHealthDetail> services = new LinkedHashMap<>();
        services.put("database", dbStatus);
        services.put("redis", redisStatus);
        services.put("opensearch", openSearchStatus);
        services.put("storage", storageStatus);
        services.put("minio", storageStatus);

        boolean allUp = services.values().stream()
                .allMatch(s -> "UP".equalsIgnoreCase(s.getStatus()));

        long totalDuration = System.currentTimeMillis() - startTime;

        HealthResponse response = HealthResponse.builder()
                .status(allUp ? "UP" : "DEGRADED")
                .timestamp(LocalDateTime.now())
                .totalDurationMs(totalDuration)
                .services(services)
                .build();

        return ResponseEntity.ok(response);
    }

    private ServiceHealthDetail checkDatabaseHealth() {
        long start = System.currentTimeMillis();
        try {
            if (dataSource == null) {
                return ServiceHealthDetail.down(System.currentTimeMillis() - start, "DataSource is not configured");
            }
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(2)) {
                    return ServiceHealthDetail.up(System.currentTimeMillis() - start, "PostgreSQL connection valid");
                } else {
                    return ServiceHealthDetail.down(System.currentTimeMillis() - start, "Connection isValid(2) check returned false");
                }
            }
        } catch (Exception e) {
            logger.warn("Health check - Database check failed: {}", e.getMessage());
            return ServiceHealthDetail.down(System.currentTimeMillis() - start, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private ServiceHealthDetail checkRedisHealth() {
        long start = System.currentTimeMillis();
        try {
            if (redisTemplate == null) {
                return ServiceHealthDetail.down(System.currentTimeMillis() - start, "RedisTemplate is not configured");
            }
            String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            long latency = System.currentTimeMillis() - start;
            if ("PONG".equalsIgnoreCase(pong)) {
                return ServiceHealthDetail.up(latency, "Redis ping returned PONG");
            } else {
                return ServiceHealthDetail.down(latency, "Redis ping returned unexpected response: " + pong);
            }
        } catch (Exception e) {
            logger.warn("Health check - Redis check failed: {}", e.getMessage());
            return ServiceHealthDetail.down(System.currentTimeMillis() - start, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private ServiceHealthDetail checkOpenSearchHealth() {
        long start = System.currentTimeMillis();
        try {
            if (openSearchClient == null) {
                return ServiceHealthDetail.down(System.currentTimeMillis() - start, "OpenSearchClient is not configured");
            }
            boolean pingOk = openSearchClient.ping().value();
            long latency = System.currentTimeMillis() - start;
            if (pingOk) {
                return ServiceHealthDetail.up(latency, "Cluster ping successful");
            } else {
                return ServiceHealthDetail.down(latency, "OpenSearch ping returned false");
            }
        } catch (Exception e) {
            logger.warn("Health check - OpenSearch check failed: {}", e.getMessage());
            return ServiceHealthDetail.down(System.currentTimeMillis() - start, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private ServiceHealthDetail checkStorageHealth() {
        long start = System.currentTimeMillis();
        try {
            if (storageService == null) {
                return ServiceHealthDetail.down(System.currentTimeMillis() - start, "StorageService is not configured");
            }
            boolean ok = storageService.isHealthy();
            long latency = System.currentTimeMillis() - start;
            if (ok) {
                return ServiceHealthDetail.up(latency, "Storage bucket accessible and responsive");
            } else {
                return ServiceHealthDetail.down(latency, "Storage service reported unhealthy");
            }
        } catch (Exception e) {
            logger.warn("Health check - Storage check failed: {}", e.getMessage());
            return ServiceHealthDetail.down(System.currentTimeMillis() - start, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
