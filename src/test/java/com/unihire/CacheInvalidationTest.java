package com.unihire;
import com.unihire.services.resume.ResumeParsingService;
import com.unihire.services.job.JobService;
import com.unihire.services.employer.EmployerService;
import com.unihire.services.candidate.ApplicationService;
import com.unihire.services.candidate.CandidateService;
import com.unihire.services.ai.RecommendationService;
import com.unihire.services.admin.AnalyticsService;
import com.unihire.services.admin.AdminService;

import com.unihire.dto.*;
import com.unihire.models.*;
import com.unihire.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify cache invalidation behaviour across all
 * critical service flows. Tests run against the full Spring context and exercise
 * actual cache infrastructure rather than mocking it.
 *
 * <p>Each test follows the AAA pattern:
 * <ol>
 *   <li><b>Arrange</b> — pre-populate the cache so a stale entry exists.</li>
 *   <li><b>Act</b>     — invoke the service method that should evict the cache.</li>
 *   <li><b>Assert</b>  — verify the cache entry is absent (evicted correctly).</li>
 * </ol>
 *
 * <p>Tests are annotated with {@code @Transactional} so DB changes roll back.
 * Cache entries are cleared in {@link #setUp} before every test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CacheInvalidationTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CandidateService candidateService;

    @Autowired
    private EmployerService employerService;

    @Autowired
    private JobService jobService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private ResumeParsingService resumeParsingService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Cache getCache(String name) {
        return cacheManager.getCache(name);
    }

    private void putInCache(String cacheName, Object key, Object value) {
        Cache cache = getCache(cacheName);
        assertThat(cache).as("Cache '%s' must exist", cacheName).isNotNull();
        cache.put(key, value);
    }

    private Object getFromCache(String cacheName, Object key) {
        Cache cache = getCache(cacheName);
        if (cache == null) return null;
        Cache.ValueWrapper wrapper = cache.get(key);
        return wrapper != null ? wrapper.get() : null;
    }

    @BeforeEach
    void setUp() {
        for (String name : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
    }

    // =========================================================================
    // 1. CANDIDATE PROFILE EVICTION
    // =========================================================================

    @Test
    @DisplayName("updateProfile() evicts candidateProfiles, dashboardMetrics, and recommendations caches")
    void updateProfile_shouldEvictAllThreeCaches() {
        Long userId = 1L;
        putInCache("candidateProfiles", userId, "stale-profile");
        putInCache("dashboardMetrics", "candidate_" + userId, "stale-dashboard");
        putInCache("recommendations", "jobs_for_candidate_" + userId, "stale-recs");

        try {
            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setFirstName("Updated");
            candidateService.updateProfile(userId, req);
        } catch (Exception ignored) {}

        assertThat(getFromCache("candidateProfiles", userId)).isNull();
        assertThat(getFromCache("dashboardMetrics", "candidate_" + userId)).isNull();
        assertThat(getFromCache("recommendations", "jobs_for_candidate_" + userId)).isNull();
    }

    @Test
    @DisplayName("addEducation() evicts candidateProfiles, dashboardMetrics, and recommendations")
    void addEducation_shouldEvictAllThreeCaches() {
        Long userId = 2L;
        putInCache("candidateProfiles", userId, "stale");
        putInCache("dashboardMetrics", "candidate_" + userId, "stale");
        putInCache("recommendations", "jobs_for_candidate_" + userId, "stale");

        try { candidateService.addEducation(userId, new EducationDto()); } catch (Exception ignored) {}

        assertThat(getFromCache("candidateProfiles", userId)).isNull();
        assertThat(getFromCache("dashboardMetrics", "candidate_" + userId)).isNull();
        assertThat(getFromCache("recommendations", "jobs_for_candidate_" + userId)).isNull();
    }

    @Test
    @DisplayName("addExperience() evicts candidateProfiles, dashboardMetrics, and recommendations")
    void addExperience_shouldEvictAllThreeCaches() {
        Long userId = 3L;
        putInCache("candidateProfiles", userId, "stale");
        putInCache("dashboardMetrics", "candidate_" + userId, "stale");
        putInCache("recommendations", "jobs_for_candidate_" + userId, "stale");

        try { candidateService.addExperience(userId, new ExperienceDto()); } catch (Exception ignored) {}

        assertThat(getFromCache("candidateProfiles", userId)).isNull();
        assertThat(getFromCache("dashboardMetrics", "candidate_" + userId)).isNull();
        assertThat(getFromCache("recommendations", "jobs_for_candidate_" + userId)).isNull();
    }

    @Test
    @DisplayName("addCertification() evicts candidateProfiles, dashboardMetrics, and recommendations")
    void addCertification_shouldEvictAllThreeCaches() {
        Long userId = 4L;
        putInCache("candidateProfiles", userId, "stale");
        putInCache("dashboardMetrics", "candidate_" + userId, "stale");
        putInCache("recommendations", "jobs_for_candidate_" + userId, "stale");

        try { candidateService.addCertification(userId, new CertificationDto()); } catch (Exception ignored) {}

        assertThat(getFromCache("candidateProfiles", userId)).isNull();
        assertThat(getFromCache("dashboardMetrics", "candidate_" + userId)).isNull();
        assertThat(getFromCache("recommendations", "jobs_for_candidate_" + userId)).isNull();
    }

    @Test
    @DisplayName("reparseResume() eagerly evicts all three candidate caches before the background job starts")
    void reparseResume_shouldEagerlyEvictCaches() {
        Long userId = 5L;
        putInCache("candidateProfiles", userId, "stale-completed-profile");
        putInCache("dashboardMetrics", "candidate_" + userId, "stale");
        putInCache("recommendations", "jobs_for_candidate_" + userId, "stale");

        try { candidateService.reparseResume(userId); } catch (Exception ignored) {}

        assertThat(getFromCache("candidateProfiles", userId)).isNull();
        assertThat(getFromCache("dashboardMetrics", "candidate_" + userId)).isNull();
        assertThat(getFromCache("recommendations", "jobs_for_candidate_" + userId)).isNull();
    }

    // =========================================================================
    // 2. RESUME PARSING BACKGROUND TASK (Async thread — programmatic eviction)
    // =========================================================================

    @Test
    @DisplayName("ResumeParsingService.parseResume() programmatically evicts candidate caches even on failure")
    void parseResume_shouldEvictCachesOnCompletion() {
        Long candidateId = 10L;
        putInCache("candidateProfiles", candidateId, "pre-parse-stale");
        putInCache("dashboardMetrics", "candidate_" + candidateId, "stale");
        putInCache("recommendations", "jobs_for_candidate_" + candidateId, "stale");

        // Invoke directly simulating background async worker
        try { resumeParsingService.parseResume(candidateId, "nonexistent.pdf"); } catch (Exception ignored) {}

        assertThat(getFromCache("candidateProfiles", candidateId)).isNull();
        assertThat(getFromCache("dashboardMetrics", "candidate_" + candidateId)).isNull();
        assertThat(getFromCache("recommendations", "jobs_for_candidate_" + candidateId)).isNull();
    }

    // =========================================================================
    // 3. ADMIN OPERATIONS EVICTION
    // =========================================================================

    @Test
    @DisplayName("AdminService.updateCandidateVerification() evicts candidateProfiles and dashboardMetrics")
    void updateCandidateVerification_shouldEvictCandidateCaches() {
        Long candidateId = 20L;
        putInCache("candidateProfiles", String.valueOf(candidateId), "stale-unverified");
        putInCache("dashboardMetrics", "candidate_" + candidateId, "stale");

        try { adminService.updateCandidateVerification(candidateId, VerificationStatus.VERIFIED); } catch (Exception ignored) {}

        assertThat(getFromCache("candidateProfiles", String.valueOf(candidateId))).isNull();
        assertThat(getFromCache("dashboardMetrics", "candidate_" + candidateId)).isNull();
    }

    @Test
    @DisplayName("AdminService.updateEmployerVerification() evicts employerProfiles cache")
    void updateEmployerVerification_shouldEvictEmployerCache() {
        Long employerId = 30L;
        putInCache("employerProfiles", String.valueOf(employerId), "stale-unverified-employer");

        try { adminService.updateEmployerVerification(employerId, VerificationStatus.VERIFIED); } catch (Exception ignored) {}

        assertThat(getFromCache("employerProfiles", String.valueOf(employerId))).isNull();
    }

    @Test
    @DisplayName("AdminService.toggleUserLock() defensively evicts candidateProfiles, dashboardMetrics, and employerProfiles")
    void toggleUserLock_shouldEvictAllProfileCachesForUser() {
        Long userId = 50L;
        putInCache("candidateProfiles", String.valueOf(userId), "stale");
        putInCache("dashboardMetrics", "candidate_" + userId, "stale");
        putInCache("employerProfiles", String.valueOf(userId), "stale");

        try { adminService.toggleUserLock(userId, new LockUserRequest(), "admin@test.com"); } catch (Exception ignored) {}

        assertThat(getFromCache("candidateProfiles", String.valueOf(userId))).isNull();
        assertThat(getFromCache("dashboardMetrics", "candidate_" + userId)).isNull();
        assertThat(getFromCache("employerProfiles", String.valueOf(userId))).isNull();
    }

    // =========================================================================
    // 4. APPLICATION LIFECYCLE EVICTION
    // =========================================================================

    @Test
    @DisplayName("ApplicationService.applyForJob() evicts dashboardMetrics and recommendations")
    void applyForJob_shouldEvictDashboardAndRecommendations() {
        Long candidateId = 60L;
        putInCache("dashboardMetrics", "candidate_" + candidateId, "stale-dashboard");
        putInCache("recommendations", "jobs_for_candidate_" + candidateId, "stale-recs");

        try {
            applicationService.applyForJob(candidateId, 100L, new ApplicationRequestDto());
        } catch (Exception ignored) {}

        assertThat(getFromCache("dashboardMetrics", "candidate_" + candidateId)).isNull();
        assertThat(getFromCache("recommendations", "jobs_for_candidate_" + candidateId)).isNull();
    }

    // =========================================================================
    // 5. JOB MUTATIONS EVICTION
    // =========================================================================

    @Test
    @DisplayName("JobService.updateJob() evicts publicJobs and recommendations::candidates_for_job")
    void updateJob_shouldEvictPublicJobsAndRecommendations() {
        Long jobId = 300L;
        putInCache("publicJobs", jobId, "stale-job");
        putInCache("recommendations", "candidates_for_job_" + jobId, "stale-recs");

        try { jobService.updateJob(jobId, 1L, new JobRequestDto()); } catch (Exception ignored) {}

        assertThat(getFromCache("publicJobs", jobId)).isNull();
        assertThat(getFromCache("recommendations", "candidates_for_job_" + jobId)).isNull();
    }

    @Test
    @DisplayName("JobService.deleteJob() evicts publicJobs and recommendations::candidates_for_job")
    void deleteJob_shouldEvictPublicJobsAndRecommendations() {
        Long jobId = 301L;
        putInCache("publicJobs", jobId, "stale-job");
        putInCache("recommendations", "candidates_for_job_" + jobId, "stale-recs");

        try { jobService.deleteJob(jobId, 1L); } catch (Exception ignored) {}

        assertThat(getFromCache("publicJobs", jobId)).isNull();
        assertThat(getFromCache("recommendations", "candidates_for_job_" + jobId)).isNull();
    }

    @Test
    @DisplayName("JobService.createJob() evicts analytics cache for the employer")
    void createJob_shouldEvictAnalyticsCache() {
        Long employerId = 220L;
        putInCache("analytics", "employer_analytics_" + employerId, "stale-analytics");

        try { jobService.createJob(employerId, new JobRequestDto()); } catch (Exception ignored) {}

        assertThat(getFromCache("analytics", "employer_analytics_" + employerId)).isNull();
    }

    // =========================================================================
    // 6. CACHE NAMESPACE ISOLATION
    // =========================================================================

    @Test
    @DisplayName("publicJobs and recommendations caches are independent — evicting one does not touch the other")
    void cacheNamespaceIsolation_publicJobsAndRecommendationsAreIndependent() {
        Long jobId = 600L;
        putInCache("publicJobs", jobId, "cached-job");
        putInCache("recommendations", "candidates_for_job_" + jobId, "cached-recs");

        // Evict only publicJobs
        Cache publicJobsCache = getCache("publicJobs");
        if (publicJobsCache != null) publicJobsCache.evict(jobId);

        assertThat(getFromCache("publicJobs", jobId)).isNull();
        // recommendations must be untouched
        assertThat(getFromCache("recommendations", "candidates_for_job_" + jobId)).isNotNull();
    }

    @Test
    @DisplayName("candidateProfiles and analytics caches are independent namespaces")
    void cacheNamespaceIsolation_candidateAndAnalyticsAreIndependent() {
        Long candidateId = 700L;
        Long employerId = 701L;
        putInCache("candidateProfiles", candidateId, "candidate-data");
        putInCache("analytics", "employer_analytics_" + employerId, "analytics-data");

        // Evict candidateProfiles only
        Cache profileCache = getCache("candidateProfiles");
        if (profileCache != null) profileCache.evict(candidateId);

        assertThat(getFromCache("candidateProfiles", candidateId)).isNull();
        // analytics must be untouched
        assertThat(getFromCache("analytics", "employer_analytics_" + employerId)).isNotNull();
    }
}
