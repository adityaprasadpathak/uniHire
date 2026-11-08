package com.unihire.services.job;

import com.unihire.models.Job;
import com.unihire.models.JobView;
import com.unihire.repositories.JobRepository;
import com.unihire.repositories.JobViewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Background synchronization service that flushes aggregated job view counts
 * buffered in Redis into PostgreSQL.
 *
 * <p>Production Write-Behind Pattern:
 * <ul>
 *   <li>During page visits, view counts are atomically incremented in Redis (0 DB load).</li>
 *   <li>Every 15 minutes (configurable via {@code unihire.job-views.sync-rate-ms}),
 *       this flusher aggregates the buffer and batch-inserts records into PostgreSQL.</li>
 * </ul>
 */
@Service
public class JobViewSyncService {

    private static final Logger logger = LoggerFactory.getLogger(JobViewSyncService.class);
    private static final String HASH_KEY = "job:views";

    private final StringRedisTemplate redisTemplate;
    private final JobRepository jobRepository;
    private final JobViewRepository jobViewRepository;

    public JobViewSyncService(StringRedisTemplate redisTemplate,
                              JobRepository jobRepository,
                              JobViewRepository jobViewRepository) {
        this.redisTemplate = redisTemplate;
        this.jobRepository = jobRepository;
        this.jobViewRepository = jobViewRepository;
    }

    /**
     * Drains the Redis job view buffer and batch-inserts views into PostgreSQL.
     * Configurable via application properties (defaults to every 15 minutes, initial delay 1 minute).
     */
    @Scheduled(
            fixedRateString = "${unihire.job-views.sync-rate-ms:900000}",
            initialDelayString = "${unihire.job-views.sync-initial-delay-ms:60000}"
    )
    @Transactional
    public void syncJobViewsToDatabase() {
        if (redisTemplate == null) {
            return;
        }

        try {
            Map<Object, Object> buffer = redisTemplate.opsForHash().entries(HASH_KEY);
            if (buffer == null || buffer.isEmpty()) {
                // Buffer is empty - return silently without logging
                return;
            }

            logger.info("Flushing aggregated job views from Redis buffer ({} jobs pending)...", buffer.size());

            LocalDateTime now = LocalDateTime.now();
            List<JobView> viewsToSave = new ArrayList<>();
            List<String> processedJobIds = new ArrayList<>();
            long totalViewsCount = 0;

            for (Map.Entry<Object, Object> entry : buffer.entrySet()) {
                String jobIdStr = (String) entry.getKey();
                String viewsStr = (String) entry.getValue();

                try {
                    Long jobId = Long.valueOf(jobIdStr);
                    int views = Integer.parseInt(viewsStr);

                    if (views <= 0) {
                        processedJobIds.add(jobIdStr);
                        continue;
                    }

                    Job job = jobRepository.findById(jobId).orElse(null);
                    if (job != null) {
                        for (int i = 0; i < views; i++) {
                            viewsToSave.add(JobView.builder()
                                    .job(job)
                                    .viewedAt(now)
                                    .build());
                        }
                        totalViewsCount += views;
                    }

                    processedJobIds.add(jobIdStr);
                } catch (NumberFormatException e) {
                    logger.error("Failed to parse jobId or view count: {} - {}", jobIdStr, viewsStr);
                    processedJobIds.add(jobIdStr);
                }
            }

            // Perform batch insert into PostgreSQL
            if (!viewsToSave.isEmpty()) {
                jobViewRepository.saveAll(viewsToSave);
            }

            // Clear flushed entries from Redis hash
            if (!processedJobIds.isEmpty()) {
                redisTemplate.opsForHash().delete(HASH_KEY, processedJobIds.toArray());
            }

            logger.info("Successfully flushed {} job views across {} jobs to database.", totalViewsCount, processedJobIds.size());

        } catch (Exception e) {
            logger.warn("Job view sync skipped: Redis is unreachable or offline ({})", e.getMessage());
        }
    }
}
