package com.unihire.services.search;
import com.unihire.services.job.JobService;

import com.unihire.models.CandidateProfile;
import com.unihire.models.Job;
import com.unihire.repositories.CandidateProfileRepository;
import com.unihire.repositories.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for reconciling the state of search indexes (OpenSearch and PgVector)
 * with the primary relational database. This ensures that any data that failed to sync during
 * normal operations is eventually captured.
 */
@Service
public class SearchReconciliationService {

    private static final Logger logger = LoggerFactory.getLogger(SearchReconciliationService.class);

    private final JobRepository jobRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final JobService jobService;
    private final SemanticSearchService semanticSearchService;

    public SearchReconciliationService(JobRepository jobRepository,
                                       CandidateProfileRepository candidateProfileRepository,
                                       JobService jobService,
                                       SemanticSearchService semanticSearchService) {
        this.jobRepository = jobRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.jobService = jobService;
        this.semanticSearchService = semanticSearchService;
    }

    /**
     * Triggers a full reconciliation process for both jobs and candidates.
     * Logs the start and completion of the process.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void reconcileAll() {
        logger.info("Reconciliation Job: Starting search reconciliation...");
        reconcileJobs();
        reconcileCandidates();
        logger.info("Reconciliation Job: Completed search reconciliation successfully.");
    }

    /**
     * Reconciles all jobs by fetching them in pages from the database
     * and pushing them to OpenSearch via the JobService.
     * Handles exceptions per job to prevent the entire batch from failing.
     */
    private void reconcileJobs() {
        logger.info("Reconciliation Job: Syncing jobs in pages of 50...");
        int page = 0;
        int size = 50;
        Page<Job> jobPage;
        
        do {
            Pageable pageable = PageRequest.of(page, size);
            Page<Job> eagerJobs = jobRepository.findAllEager(pageable);
            for (Job job : eagerJobs.getContent()) {
                try {
                    jobService.syncToOpenSearch(job);
                } catch (Exception e) {
                    logger.error("Reconciliation Job: Failed to sync job ID {}: {}", job.getId(), e.getMessage());
                }
            }
            jobPage = eagerJobs;
            page++;
        } while (jobPage.hasNext());
        logger.info("Reconciliation Job: Finished syncing all jobs.");
    }

    /**
     * Reconciles all candidate profiles by fetching them in pages from the database
     * and pushing them to PgVector and OpenSearch via the SemanticSearchService.
     * Handles exceptions per candidate to prevent the entire batch from failing.
     */
    private void reconcileCandidates() {
        logger.info("Reconciliation Job: Syncing candidates in pages of 50...");
        int page = 0;
        int size = 50;
        Page<CandidateProfile> candidatePage;

        do {
            Pageable pageable = PageRequest.of(page, size);
            candidatePage = candidateProfileRepository.findAll(pageable);
            for (CandidateProfile profile : candidatePage.getContent()) {
                try {
                    semanticSearchService.indexCandidateProfileAsync(profile.getId());
                } catch (Exception e) {
                    logger.error("Reconciliation Job: Failed to sync candidate ID {}: {}", profile.getId(), e.getMessage());
                }
            }
            page++;
        } while (candidatePage.hasNext());
        logger.info("Reconciliation Job: Finished syncing all candidates.");
    }
}
