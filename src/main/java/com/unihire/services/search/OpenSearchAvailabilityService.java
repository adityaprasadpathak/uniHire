package com.unihire.services.search;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpenSearchAvailabilityService {
    private static final Logger logger = LoggerFactory.getLogger(OpenSearchAvailabilityService.class);

    private final OpenSearchClient openSearchClient;
    private volatile boolean lastKnownStatus = false;
    private volatile long lastCheckTime = 0;
    private static final long CHECK_INTERVAL = 10000; // 10 seconds

    public OpenSearchAvailabilityService(OpenSearchClient openSearchClient) {
        this.openSearchClient = openSearchClient;
        checkAvailability(true); // Eager check on startup
    }

    public boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime > CHECK_INTERVAL) {
            checkAvailability(false);
        }
        return lastKnownStatus;
    }

    public void setUnavailable() {
        if (lastKnownStatus) {
            lastKnownStatus = false;
            logger.info("OpenSearch became UNAVAILABLE (marked manually)");
        }
    }

    private synchronized void checkAvailability(boolean isStartup) {
        long now = System.currentTimeMillis();
        if (!isStartup && (now - lastCheckTime <= CHECK_INTERVAL)) {
            return;
        }

        boolean currentStatus;
        try {
            currentStatus = openSearchClient.ping().value();
        } catch (Exception e) {
            logger.warn("OpenSearch ping check failed: {}", e.getMessage(), e);
            currentStatus = false;
        }

        if (currentStatus != lastKnownStatus || isStartup) {
            if (currentStatus) {
                logger.info("OpenSearch became AVAILABLE");
            } else {
                logger.info("OpenSearch became UNAVAILABLE");
            }
        }

        lastKnownStatus = currentStatus;
        lastCheckTime = now;
    }
}
