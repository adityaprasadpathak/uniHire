package com.unihire.services.search;

import com.unihire.dto.CandidateSummaryDto;
import com.unihire.models.CandidateProfile;
import com.unihire.models.Experience;
import com.unihire.models.Job;
import com.unihire.repositories.CandidateProfileRepository;
import com.unihire.repositories.JobRepository;
import com.unihire.opensearch.CandidateDocument;
import com.unihire.opensearch.CandidateSearchRepository;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.core.env.Environment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service class responsible for indexing and searching candidates and jobs
 * using semantic search via PgVector (Spring AI) and OpenSearch.
 * It provides cascading search capabilities and async indexing.
 */
@Service
public class SemanticSearchService {

    private static final Logger logger = LoggerFactory.getLogger(SemanticSearchService.class);

    private final VectorStore vectorStore;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateSearchRepository candidateSearchRepository;
    private final OpenSearchClient openSearchClient;
    private final OpenSearchAvailabilityService openSearchAvailabilityService;
    private final TransactionTemplate transactionTemplate;
    private final Environment environment;
    private final SemanticSearchService self;

    public SemanticSearchService(VectorStore vectorStore,
                                 CandidateProfileRepository candidateProfileRepository,
                                 CandidateSearchRepository candidateSearchRepository,
                                 OpenSearchClient openSearchClient,
                                 OpenSearchAvailabilityService openSearchAvailabilityService,
                                 TransactionTemplate transactionTemplate,
                                 Environment environment,
                                 @Lazy SemanticSearchService self) {
        this.vectorStore = vectorStore;
        this.candidateProfileRepository = candidateProfileRepository;
        this.candidateSearchRepository = candidateSearchRepository;
        this.openSearchClient = openSearchClient;
        this.openSearchAvailabilityService = openSearchAvailabilityService;
        this.transactionTemplate = transactionTemplate;
        this.environment = environment;
        this.self = self;
    }

    /**
     * Triggered on application startup to index all existing candidate profiles.
     * Clears the existing OpenSearch candidate index and re-indexes all candidates
     * from the database to both PgVector and OpenSearch.
     * Runs only when the 'local' profile is active.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void indexAllCandidatesOnStartup() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        if (activeProfiles.contains("prod") || !activeProfiles.contains("local")) {
            logger.info("Skipping candidate startup index resync: environment is not local development.");
            return;
        }
        boolean syncOnStartup = environment.getProperty("spring.opensearch.sync-on-startup", Boolean.class, false);
        if (!syncOnStartup) {
            logger.info("Skipping candidate startup index resync: 'spring.opensearch.sync-on-startup' is disabled (default).");
            return;
        }
        logger.info("Initializing vector store and OpenSearch with existing candidates...");
        if (openSearchAvailabilityService.isAvailable()) {
            try {
                candidateSearchRepository.deleteAll();
                logger.info("Cleared existing candidate index in OpenSearch.");
            } catch (Exception e) {
                logger.error("Failed to clear OpenSearch candidates: {}", e.getMessage());
            }
        }
        List<CandidateProfile> profiles = candidateProfileRepository.findAll();
        for (CandidateProfile profile : profiles) {
            self.indexCandidateProfileAsync(profile.getId());
        }
        logger.info("Indexed {} candidates.", profiles.size());
    }

    /**
     * Helper method to construct the full name of a candidate.
     * Falls back to the user's email if first and last names are missing.
     *
     * @param profile the candidate profile
     * @return the constructed full name or a fallback identifier
     */
    private String getCandidateFullName(CandidateProfile profile) {
        String firstName = profile.getFirstName();
        String lastName = profile.getLastName();
        if ((firstName == null || firstName.isEmpty()) && (lastName == null || lastName.isEmpty())) {
            return profile.getUser() != null ? profile.getUser().getEmail() : "Unnamed Candidate";
        }
        return ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
    }

    /**
     * Asynchronously indexes a specific candidate profile into both PgVector and OpenSearch.
     * Extracts skills, experiences, and other metadata to build a rich search document.
     * Executes database reads within a transaction and external indexing outside of it.
     *
     * @param candidateId the ID of the candidate to index
     */
    @Async
    public void indexCandidateProfileAsync(Long candidateId) {
        try {
            // Read from DB inside transactional template
            CandidateDocument candidateDoc = transactionTemplate.execute(status -> {
                CandidateProfile profile = candidateProfileRepository.findById(candidateId).orElse(null);
                if (profile == null) return null;

                // 1. Build experiences summary
                StringBuilder experiencesContent = new StringBuilder();
                if (profile.getExperiences() != null && !profile.getExperiences().isEmpty()) {
                    for (Experience exp : profile.getExperiences()) {
                        experiencesContent.append(exp.getJobTitle()).append(" at ").append(exp.getCompany()).append(". ");
                        if (exp.getDescription() != null) {
                            experiencesContent.append(exp.getDescription()).append(" ");
                        }
                    }
                }

                // 2. Build searchable vector content
                StringBuilder content = new StringBuilder();
                content.append("Name: ").append(getCandidateFullName(profile)).append("\n");
                
                if (profile.getSkills() != null && !profile.getSkills().isEmpty()) {
                    content.append("Skills: ").append(String.join(", ", profile.getSkills())).append("\n");
                }
                
                if (!experiencesContent.isEmpty()) {
                    content.append("Experience:\n").append(experiencesContent).append("\n");
                }

                Document vectorDoc = new Document(
                        java.util.UUID.nameUUIDFromBytes(("candidate_" + candidateId).getBytes()).toString(),
                        content.toString(),
                        Map.of(
                                "type", "candidate",
                                "status", "ACTIVE",
                                "id", candidateId.toString(),
                                "name", getCandidateFullName(profile),
                                "skills", profile.getSkills() != null ? String.join(",", profile.getSkills()) : ""
                        )
                );

                // Save to pgvector synchronously within execution context
                try {
                    vectorStore.add(List.of(vectorDoc));
                    logger.info("Successfully indexed candidate profile {} into pgvector", candidateId);
                } catch (Exception e) {
                    logger.error("Failed to index candidate profile {} into pgvector: {}", candidateId, e.getMessage());
                }

                // Return OpenSearch document details to save outside transaction
                return CandidateDocument.builder()
                        .id(profile.getId().toString())
                        .candidateId(profile.getId())
                        .fullName(getCandidateFullName(profile))
                        .headline("") // Can be populated if headline exists
                        .skills(profile.getSkills() != null ? String.join(",", profile.getSkills()) : "")
                        .experience(experiencesContent.toString())
                        .location("") // Can be populated if location exists
                        .summary("") // Can be populated if summary exists
                        .email(profile.getUser() != null ? profile.getUser().getEmail() : "")
                        .updatedAt(LocalDateTime.now())
                        .build();
            });

            // 3. Save to OpenSearch if available and document was prepared
            if (candidateDoc != null && openSearchAvailabilityService.isAvailable()) {
                try {
                    candidateSearchRepository.save(candidateDoc);
                    logger.info("Successfully indexed candidate profile {} into OpenSearch", candidateId);
                } catch (Exception e) {
                    logger.error("Failed to index candidate profile {} into OpenSearch: {}", candidateId, e.getMessage());
                    openSearchAvailabilityService.setUnavailable();
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to execute async index task for candidate {}: {}", candidateId, e.getMessage());
        }
    }

    /**
     * Indexes a job posting into PgVector for semantic search.
     * Constructs a text document combining title, description, skills, location, and department.
     *
     * @param job the job entity to index
     */
    public void indexJob(Job job) {
        try {
            StringBuilder content = new StringBuilder();
            content.append("Title: ").append(job.getTitle()).append("\n");
            content.append("Description: ").append(job.getDescription() != null ? job.getDescription() : "").append("\n");
            
            if (job.getResponsibilities() != null && !job.getResponsibilities().isEmpty()) {
                content.append("Responsibilities: ").append(job.getResponsibilities()).append("\n");
            }
            if (job.getQualifications() != null && !job.getQualifications().isEmpty()) {
                content.append("Qualifications: ").append(job.getQualifications()).append("\n");
            }
            if (job.getPreferredSkills() != null && !job.getPreferredSkills().isEmpty()) {
                content.append("Preferred Skills: ").append(job.getPreferredSkills()).append("\n");
            }
            if (job.getCertifications() != null && !job.getCertifications().isEmpty()) {
                content.append("Certifications: ").append(job.getCertifications()).append("\n");
            }
            if (job.getJobLevel() != null) {
                content.append("Job Level: ").append(job.getJobLevel()).append("\n");
            }
            if (job.getWorkMode() != null) {
                content.append("Work Mode: ").append(job.getWorkMode()).append("\n");
            }
            if (job.getEducation() != null) {
                content.append("Education: ").append(job.getEducation()).append("\n");
            }
            if (job.getLanguages() != null) {
                content.append("Languages: ").append(job.getLanguages()).append("\n");
            }

            if (job.getSkills() != null && !job.getSkills().isEmpty()) {
                content.append("Skills: ").append(job.getSkills()).append("\n");
            }
            if (job.getLocation() != null && !job.getLocation().isEmpty()) {
                content.append("Location: ").append(job.getLocation()).append("\n");
            }
            if (job.getDepartment() != null && !job.getDepartment().isEmpty()) {
                content.append("Department: ").append(job.getDepartment()).append("\n");
            }

            Document document = new Document(
                    java.util.UUID.nameUUIDFromBytes(("job_" + job.getId()).getBytes()).toString(),
                    content.toString(),
                    Map.of(
                            "type", "job",
                            "status", job.getStatus().name(),
                            "id", job.getId().toString(),
                            "title", job.getTitle(),
                            "location", job.getLocation() != null ? job.getLocation() : "",
                            "department", job.getDepartment() != null ? job.getDepartment() : ""
                    )
            );
            vectorStore.add(List.of(document));
            logger.info("Successfully indexed job {} into pgvector", job.getId());
        } catch (Exception e) {
            logger.error("Failed to index job {} into pgvector: {}", job.getId(), e.getMessage());
        }
    }

    /**
     * Searches for candidates based on a natural language query using a cascading strategy.
     * First attempts OpenSearch. If unavailable, falls back to PgVector semantic search.
     * If both fail, falls back to basic JPA querying.
     *
     * @param query the natural language search query
     * @param topK the maximum number of results to return
     * @return a list of candidate summaries matching the query
     */
    public List<CandidateSummaryDto> searchCandidates(String query, int topK) {
        // 1. Try OpenSearch Search
        try {
            if (openSearchAvailabilityService.isAvailable()) {
                SearchResponse<CandidateDocument> searchResponse = openSearchClient.search(s -> s
                        .index("candidates")
                        .query(q -> q.multiMatch(mm -> mm
                                .query(query)
                                .fields("fullName", "skills", "experience", "headline", "summary")
                        ))
                        .size(topK),
                        CandidateDocument.class
                );

                logger.info("Retrieved candidate search results from OpenSearch for query: '{}'", query);
                Double maxScore = searchResponse.hits().maxScore();
                double normalizationFactor = (maxScore == null || Double.isNaN(maxScore) || maxScore <= 0.0) ? 1.0 : maxScore;

                return searchResponse.hits().hits().stream().map(hit -> {
                    CandidateDocument doc = hit.source();
                    CandidateSummaryDto dto = new CandidateSummaryDto();
                    if (doc != null) {
                        dto.setCandidateId(doc.getCandidateId());
                        dto.setName(doc.getFullName());
                        if (doc.getSkills() != null && !doc.getSkills().isEmpty()) {
                            dto.setTopSkills(List.of(doc.getSkills().split(",")));
                        }
                    }
                    double score = hit.score() != null ? hit.score() : 0.0;
                    dto.setSimilarityScore(score / normalizationFactor);
                    return dto;
                }).collect(Collectors.toList());
            }
        } catch (Exception e) {
            logger.warn("OpenSearch candidate search failed, falling back to pgvector: {}", e.getMessage());
            openSearchAvailabilityService.setUnavailable();
        }

        // 2. Fallback to PgVector Search
        return searchCandidatesPgVector(query, topK);
    }

    /**
     * Performs a semantic search for candidates using PgVector.
     *
     * @param query the search query
     * @param topK the maximum number of results to return
     * @return a list of matching candidate summaries
     */
    private List<CandidateSummaryDto> searchCandidatesPgVector(String query, int topK) {
        try {
            List<Document> documents = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .filterExpression("type == 'candidate' && status == 'ACTIVE'")
                            .build()
            );

            logger.info("Retrieved candidate search results from PgVector for query: '{}'", query);
            return documents.stream().map(doc -> {
                CandidateSummaryDto dto = new CandidateSummaryDto();
                Map<String, Object> metadata = doc.getMetadata();
                if (metadata.containsKey("id")) {
                    dto.setCandidateId(Long.valueOf(metadata.get("id").toString()));
                }
                if (metadata.containsKey("name")) {
                    dto.setName(metadata.get("name").toString());
                }
                if (metadata.containsKey("skills")) {
                    String skillsStr = metadata.get("skills").toString();
                    if (!skillsStr.isEmpty()) {
                        dto.setTopSkills(List.of(skillsStr.split(",")));
                    }
                }
                if (metadata.containsKey("distance")) {
                    Double distance = Double.valueOf(metadata.get("distance").toString());
                    dto.setSimilarityScore(1.0 - distance);
                }
                return dto;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("PgVector candidate search failed, falling back to JPA: {}", e.getMessage());
        }

        // 3. Fallback to JPA search
        return searchCandidatesJPA(query, topK);
    }

    /**
     * Performs a basic fallback search for candidates using JPA.
     * Used when vector databases are unavailable. Note that similarity scores are 0.0.
     *
     * @param query the search query
     * @param topK the maximum number of results to return
     * @return a list of matching candidate summaries
     */
    private List<CandidateSummaryDto> searchCandidatesJPA(String query, int topK) {
        logger.info("Retrieved candidate search results from JPA for query: '{}'", query);
        List<CandidateProfile> profiles = candidateProfileRepository.searchCandidatesJPA(query, Pageable.ofSize(topK));
        return profiles.stream().map(profile -> {
            CandidateSummaryDto dto = new CandidateSummaryDto();
            dto.setCandidateId(profile.getId());
            dto.setName(getCandidateFullName(profile));
            dto.setTopSkills(profile.getSkills());
            dto.setSimilarityScore(0.0); // No similarity score available in dynamic JPA Like queries
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Deletes a job from the PgVector index.
     *
     * @param jobId the ID of the job to delete
     */
    public void deleteJob(Long jobId) {
        try {
            String vectorDocId = java.util.UUID.nameUUIDFromBytes(("job_" + jobId).getBytes()).toString();
            vectorStore.delete(List.of(vectorDocId));
            logger.info("Successfully deleted job {} from pgvector", jobId);
        } catch (Exception e) {
            logger.error("Failed to delete job {} from pgvector: {}", jobId, e.getMessage());
        }
    }
}
