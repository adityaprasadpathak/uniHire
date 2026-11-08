package com.unihire.services.job;
import com.unihire.services.search.OpenSearchAvailabilityService;

import com.unihire.dto.JobResponseDto;
import com.unihire.opensearch.JobDocument;
import com.unihire.exceptions.JobExpiredException;
import com.unihire.exceptions.RateLimitExceededException;
import com.unihire.exceptions.ResourceNotFoundException;
import com.unihire.models.Job;
import com.unihire.models.JobStatus;
import com.unihire.models.JobView;
import com.unihire.repositories.JobRepository;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Service class for handling public-facing job search and retrieval operations.
 * Implements a cascading search strategy (OpenSearch -> PgVector -> JPA) for optimal performance and relevance.
 */
@Service
public class PublicJobService {

    private static final Logger logger = LoggerFactory.getLogger(PublicJobService.class);

    private final JobRepository jobRepository;
    private final OpenSearchClient openSearchClient;
    private final com.unihire.repositories.JobViewRepository jobViewRepository;
    private final OpenSearchAvailabilityService openSearchAvailabilityService;
    private final VectorStore vectorStore;
    private final StringRedisTemplate redisTemplate;

    public PublicJobService(JobRepository jobRepository,
                            OpenSearchClient openSearchClient,
                            com.unihire.repositories.JobViewRepository jobViewRepository,
                            OpenSearchAvailabilityService openSearchAvailabilityService,
                            VectorStore vectorStore,
                            StringRedisTemplate redisTemplate) {
        this.jobRepository = jobRepository;
        this.openSearchClient = openSearchClient;
        this.jobViewRepository = jobViewRepository;
        this.openSearchAvailabilityService = openSearchAvailabilityService;
        this.vectorStore = vectorStore;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Searches for jobs based on a keyword, location, and department with a cascading fallback strategy.
     * If no keyword is provided, it routes directly to a JPA query.
     * If a keyword is provided, it first attempts an OpenSearch query. If that fails or is unavailable,
     * it falls back to a PgVector semantic search. If that also fails, it relies on JPA.
     *
     * @param keyword the search term (optional)
     * @param location the desired job location (optional)
     * @param department the desired department (optional)
     * @param pageable pagination and sorting information
     * @return a paginated list of matching JobResponseDto objects
     */
    public Page<JobResponseDto> searchJobs(String keyword, String location, String department, Pageable pageable) {
        // Keyword absent: route directly to JPA
        if (keyword == null || keyword.trim().isEmpty()) {
            return searchJobsJPA(null, location, department, pageable);
        }

        // Keyword present: Try OpenSearch -> PgVector -> JPA
        try {
            if (openSearchAvailabilityService.isAvailable()) {
                return searchJobsOpenSearch(keyword, location, department, pageable);
            }
        } catch (Exception ex) {
            logger.warn("OpenSearch job search failed, falling back to PgVector: {}", ex.getMessage());
            openSearchAvailabilityService.setUnavailable();
        }

        try {
            return searchJobsPgVector(keyword, location, department, pageable);
        } catch (Exception ex) {
            logger.warn("PgVector job search failed, falling back to JPA: {}", ex.getMessage());
        }

        return searchJobsJPA(keyword, location, department, pageable);
    }

    /**
     * Performs a job search using OpenSearch.
     * Constructs a BoolQuery applying constraints for status (PUBLISHED) and unexpired jobs,
     * along with multi-match queries for the keyword and specific terms for location and department.
     *
     * @param keyword the search term
     * @param location the desired job location
     * @param department the desired department
     * @param pageable pagination and sorting information
     * @return a paginated list of matching JobResponseDto objects
     */
    private Page<JobResponseDto> searchJobsOpenSearch(String keyword, String location, String department, Pageable pageable) throws Exception {
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

        // Must be PUBLISHED
        boolQueryBuilder.must(m -> m.term(t -> t.field("status").value(FieldValue.of("PUBLISHED"))));

        // Expiry date logic (either null or > now). OpenSearch date range query:
        String nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        boolQueryBuilder.must(m -> m.bool(b -> b
                .should(s -> s.range(r -> r.field("expiryDate").gt(JsonData.of(nowStr))))
                .should(s -> s.bool(b2 -> b2.mustNot(mn -> mn.exists(e -> e.field("expiryDate")))))
                .minimumShouldMatch("1")
        ));

        // Keyword in Title or Description
        boolQueryBuilder.must(m -> m.multiMatch(mm -> mm
                .query(keyword)
                .fields("title", "description", "skills")
        ));

        // Location
        if (location != null && !location.trim().isEmpty()) {
            boolQueryBuilder.must(m -> m.match(ma -> ma
                    .field("location")
                    .query(FieldValue.of(location))
            ));
        }

        // Department (Exact Match)
        if (department != null && !department.trim().isEmpty()) {
            boolQueryBuilder.must(m -> m.term(t -> t.field("department").value(FieldValue.of(department))));
        }

        Query query = Query.of(q -> q.bool(boolQueryBuilder.build()));

        List<SortOptions> sortOptions = new ArrayList<>();
        if (pageable.getSort().isSorted()) {
            pageable.getSort().forEach(order -> {
                sortOptions.add(SortOptions.of(so -> so
                        .field(f -> f
                                .field(order.getProperty())
                                .order(order.isAscending() ? SortOrder.Asc : SortOrder.Desc)
                        )
                ));
            });
        }

        SearchResponse<JobDocument> searchResponse = openSearchClient.search(s -> {
            s.index("jobs")
             .query(query)
             .from((int) pageable.getOffset())
             .size(pageable.getPageSize());
            if (!sortOptions.isEmpty()) {
                s.sort(sortOptions);
            }
            return s;
        }, JobDocument.class);
        
        List<JobResponseDto> content = searchResponse.hits().hits().stream()
                .map(Hit::source)
                .filter(java.util.Objects::nonNull)
                .map(this::mapDocumentToDto)
                .collect(Collectors.toList());

        long totalHits = searchResponse.hits().total() != null ? searchResponse.hits().total().value() : content.size();
        logger.info("Retrieved job search results from OpenSearch for keyword: '{}'", keyword);
        return new PageImpl<>(content, pageable, totalHits);
    }

    /**
     * Performs a semantic job search using PgVector via Spring AI.
     * Constructs a filter expression for status, location, and department,
     * and performs a similarity search based on the keyword.
     *
     * @param keyword the semantic search term
     * @param location the desired job location
     * @param department the desired department
     * @param pageable pagination and sorting information
     * @return a paginated list of matching JobResponseDto objects
     */
    private Page<JobResponseDto> searchJobsPgVector(String keyword, String location, String department, Pageable pageable) {
        StringBuilder filter = new StringBuilder("type == 'job' && status == 'PUBLISHED'");
        if (location != null && !location.trim().isEmpty()) {
            filter.append(" && location == '").append(location.trim().replace("'", "\\'")).append("'");
        }
        if (department != null && !department.trim().isEmpty()) {
            filter.append(" && department == '").append(department.trim().replace("'", "\\'")).append("'");
        }

        int limit = (int) (pageable.getOffset() + pageable.getPageSize());
        if (limit <= 0) limit = 10;

        SearchRequest searchRequest = SearchRequest.builder()
                .query(keyword)
                .topK(limit)
                .filterExpression(filter.toString())
                .build();

        List<Document> documents = vectorStore.similaritySearch(searchRequest);

        // Apply pagination to the returned list
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), documents.size());
        
        List<JobResponseDto> content = List.of();
        if (start < documents.size()) {
            content = documents.subList(start, end).stream()
                    .map(doc -> {
                        Long jobId = Long.valueOf(doc.getMetadata().get("jobId").toString());
                        return jobRepository.findById(jobId).map(this::mapToDto).orElse(null);
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        }

        logger.info("Retrieved job search results from PgVector for keyword: '{}'", keyword);
        return new PageImpl<>(content, pageable, documents.size());
    }

    /**
     * Performs a basic database job search using JPA Specifications.
     * Used as a fallback or for straightforward queries without keywords.
     *
     * @param keyword the search term
     * @param location the desired job location
     * @param department the desired department
     * @param pageable pagination and sorting information
     * @return a paginated list of matching JobResponseDto objects
     */
    private Page<JobResponseDto> searchJobsJPA(String keyword, String location, String department, Pageable pageable) {
        logger.info("Retrieved job search results from JPA for keyword: '{}', location: '{}', department: '{}'", keyword, location, department);
        Page<Job> jobs = jobRepository.findAll(
                com.unihire.repositories.JobSpecification.searchPublicJobs(keyword, location, department),
                pageable
        );
        return jobs.map(this::mapToDto);
    }

    /**
     * Retrieves all published jobs for a specific employer.
     * Uses OpenSearch if available, otherwise falls back to JPA.
     *
     * @param employerId the ID of the employer
     * @param pageable pagination and sorting information
     * @return a paginated list of the employer's JobResponseDto objects
     */
    public Page<JobResponseDto> getEmployerJobs(Long employerId, Pageable pageable) {
        try {
            if (openSearchAvailabilityService.isAvailable()) {
                return getEmployerJobsOpenSearch(employerId, pageable);
            }
        } catch (Exception e) {
            logger.warn("OpenSearch getEmployerJobs failed, falling back to JPA: {}", e.getMessage());
            openSearchAvailabilityService.setUnavailable();
        }
        return getEmployerJobsJPA(employerId, pageable);
    }

    /**
     * Retrieves an employer's jobs from OpenSearch.
     *
     * @param employerId the ID of the employer
     * @param pageable pagination and sorting information
     * @return a paginated list of the employer's JobResponseDto objects
     */
    private Page<JobResponseDto> getEmployerJobsOpenSearch(Long employerId, Pageable pageable) throws Exception {
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

        boolQueryBuilder.must(m -> m.term(t -> t.field("status").value(FieldValue.of("PUBLISHED"))));
        boolQueryBuilder.must(m -> m.term(t -> t.field("employerId").value(FieldValue.of(employerId))));

        String nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        boolQueryBuilder.must(m -> m.bool(b -> b
                .should(s -> s.range(r -> r.field("expiryDate").gt(JsonData.of(nowStr))))
                .should(s -> s.bool(b2 -> b2.mustNot(mn -> mn.exists(e -> e.field("expiryDate")))))
                .minimumShouldMatch("1")
        ));

        Query query = Query.of(q -> q.bool(boolQueryBuilder.build()));

        List<SortOptions> sortOptions = new ArrayList<>();
        if (pageable.getSort().isSorted()) {
            pageable.getSort().forEach(order -> {
                sortOptions.add(SortOptions.of(so -> so
                        .field(f -> f
                                .field(order.getProperty())
                                .order(order.isAscending() ? SortOrder.Asc : SortOrder.Desc)
                        )
                ));
            });
        }

        SearchResponse<JobDocument> searchResponse = openSearchClient.search(s -> {
            s.index("jobs")
             .query(query)
             .from((int) pageable.getOffset())
             .size(pageable.getPageSize());
            if (!sortOptions.isEmpty()) {
                s.sort(sortOptions);
            }
            return s;
        }, JobDocument.class);
        
        List<JobResponseDto> content = searchResponse.hits().hits().stream()
                .map(Hit::source)
                .filter(java.util.Objects::nonNull)
                .map(this::mapDocumentToDto)
                .collect(Collectors.toList());

        long totalHits = searchResponse.hits().total() != null ? searchResponse.hits().total().value() : content.size();
        logger.info("Retrieved employer {} jobs from OpenSearch", employerId);
        return new PageImpl<>(content, pageable, totalHits);
    }

    /**
     * Retrieves an employer's jobs from the relational database via JPA.
     *
     * @param employerId the ID of the employer
     * @param pageable pagination and sorting information
     * @return a paginated list of the employer's JobResponseDto objects
     */
    private Page<JobResponseDto> getEmployerJobsJPA(Long employerId, Pageable pageable) {
        logger.info("Retrieved employer {} jobs from JPA", employerId);
        Page<Job> jobs = jobRepository.findByEmployerIdAndStatus(employerId, com.unihire.models.JobStatus.PUBLISHED, pageable);
        return jobs.map(this::mapToDto);
    }

    /**
     * Retrieves detailed information for a specific public job.
     * Ensures the job is published and hasn't expired.
     * Increments the job's view count asynchronously using Redis and the database.
     * Results are cached for 10 minutes based on the job ID.
     *
     * @param jobId the ID of the job to retrieve
     * @param clientIp the IP address of the client making the request, used for view deduplication
     * @return the JobResponseDto representing the job
     * @throws ResourceNotFoundException if the job does not exist
     * @throws JobExpiredException if the job has expired
     */
    @Cacheable(value = "publicJobs", key = "#jobId")
    public JobResponseDto getJobDetails(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (job.getStatus() != JobStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Job is not available");
        }

        if (job.getExpiryDate() != null && job.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new JobExpiredException("Job has expired");
        }

        return mapToDto(job);
    }

    public JobResponseDto getJobById(Long jobId, String clientIp) {
        trackJobView(jobId, clientIp);
        return getJobDetails(jobId);
    }

    public void recordJobView(Long jobId, Long viewerId, String sessionId) {
        String identifier = viewerId != null ? "usr_" + viewerId : (sessionId != null ? "sess_" + sessionId : null);
        if (identifier != null && redisTemplate != null) {
            String dedupeKey = "view_limit:" + jobId + ":" + identifier;
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupeKey, "1", Duration.ofMinutes(30));
            if (Boolean.FALSE.equals(isNew)) {
                throw new RateLimitExceededException("Already viewed recently");
            }
        }

        // Buffer the view count in Redis (0 database writes on request path)
        if (redisTemplate != null) {
            redisTemplate.opsForHash().increment("job:views", jobId.toString(), 1);
        }
    }

    private void trackJobView(Long jobId, String clientIp) {
        if (clientIp == null || clientIp.trim().isEmpty() || redisTemplate == null) {
            return;
        }

        String rateLimitKey = "view_limit:" + jobId + ":" + clientIp;
        Boolean isFirstView = redisTemplate.opsForValue().setIfAbsent(rateLimitKey, "1", Duration.ofHours(24));

        if (Boolean.TRUE.equals(isFirstView)) {
            // Buffer unique view count in Redis (0 database writes on request path)
            redisTemplate.opsForHash().increment("job:views", jobId.toString(), 1);
        }
    }

    /**
     * Maps an OpenSearch JobDocument to a JobResponseDto.
     *
     * @param doc the OpenSearch JobDocument
     * @return the mapped JobResponseDto
     */
    private JobResponseDto mapDocumentToDto(JobDocument doc) {
        JobResponseDto dto = new JobResponseDto();
        dto.setId(Long.valueOf(doc.getId()));
        dto.setTitle(doc.getTitle());
        dto.setDescription(doc.getDescription());
        dto.setExperience(doc.getExperience());
        dto.setSalary(doc.getSalary());
        dto.setSkills(doc.getSkills());
        dto.setLocation(doc.getLocation());
        dto.setDepartment(doc.getDepartment());
        dto.setEmploymentType(doc.getEmploymentType());
        dto.setStatus(doc.getStatus());
        dto.setExpiryDate(doc.getExpiryDate());
        dto.setPublishedAt(doc.getPublishedAt());
        return dto;
    }

    /**
     * Maps a JPA Job entity to a JobResponseDto.
     *
     * @param job the JPA Job entity
     * @return the mapped JobResponseDto
     */
    private JobResponseDto mapToDto(Job job) {
        JobResponseDto dto = new JobResponseDto();
        dto.setId(job.getId());
        dto.setTitle(job.getTitle());
        dto.setDescription(job.getDescription());
        dto.setExperience(job.getExperience());
        dto.setMinExperience(job.getMinExperience());
        dto.setMaxExperience(job.getMaxExperience());
        dto.setSalary(job.getSalary());
        dto.setSkills(job.getSkills());
        dto.setPreferredSkills(job.getPreferredSkills());
        dto.setLocation(job.getLocation());
        dto.setDepartment(job.getDepartment());
        dto.setEmploymentType(job.getEmploymentType());
        dto.setNumberOfVacancies(job.getNumberOfVacancies());
        dto.setBenefits(job.getBenefits());
        dto.setStatus(job.getStatus().name());
        dto.setCreatedAt(job.getCreatedAt());
        dto.setUpdatedAt(job.getUpdatedAt());
        dto.setPublishedAt(job.getPublishedAt());
        dto.setClosedAt(job.getClosedAt());
        dto.setExpiryDate(job.getExpiryDate());
        // Additional structured recruitment fields
        dto.setJobLevel(job.getJobLevel());
        dto.setWorkMode(job.getWorkMode());
        dto.setOpenings(job.getOpenings());
        dto.setEducation(job.getEducation());
        dto.setCertifications(job.getCertifications());
        dto.setLanguages(job.getLanguages());
        dto.setResponsibilities(job.getResponsibilities());
        dto.setQualifications(job.getQualifications());
        dto.setWorkingDays(job.getWorkingDays());
        dto.setShiftTiming(job.getShiftTiming());
        dto.setTravelRequirement(job.getTravelRequirement());
        dto.setNoticePeriod(job.getNoticePeriod());
        dto.setEmploymentCategory(job.getEmploymentCategory());
        dto.setIndustry(job.getIndustry());
        dto.setIncentives(job.getIncentives());
        dto.setHiringManager(job.getHiringManager());
        dto.setRecruiter(job.getRecruiter());
        dto.setInterviewProcess(job.getInterviewProcess());
        dto.setScreeningQuestions(job.getScreeningQuestions());
        if (job.getEmployer() != null) {
            dto.setCompanyName(job.getEmployer().getCompanyName());
        }
        return dto;
    }
}
