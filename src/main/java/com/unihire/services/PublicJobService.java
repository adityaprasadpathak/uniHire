package com.unihire.services;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.unihire.dto.JobResponseDto;
import com.unihire.elasticsearch.JobDocument;
import com.unihire.exceptions.JobExpiredException;
import com.unihire.exceptions.RateLimitExceededException;
import com.unihire.exceptions.ResourceNotFoundException;
import com.unihire.models.Job;
import com.unihire.models.JobStatus;
import com.unihire.models.JobView;
import com.unihire.repositories.JobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PublicJobService {

    private final JobRepository jobRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final com.unihire.repositories.JobViewRepository jobViewRepository;

    public PublicJobService(JobRepository jobRepository, ElasticsearchOperations elasticsearchOperations, com.unihire.repositories.JobViewRepository jobViewRepository) {
        this.jobRepository = jobRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.jobViewRepository = jobViewRepository;
    }

    public Page<JobResponseDto> searchJobs(String keyword, String location, String department, Pageable pageable) {
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

        // Must be PUBLISHED
        boolQueryBuilder.must(m -> m.term(t -> t.field("status").value("PUBLISHED")));

        // Expiry date logic (either null or > now). ES date range query:
        // Because expiryDate could be null, we use a should clause
        String nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        boolQueryBuilder.must(m -> m.bool(b -> b
                .should(s -> s.range(r -> r.date(d -> d.field("expiryDate").gt(nowStr))))
                .should(s -> s.bool(b2 -> b2.mustNot(mn -> mn.exists(e -> e.field("expiryDate")))))
                .minimumShouldMatch("1")
        ));

        // Keyword in Title or Description
        if (keyword != null && !keyword.trim().isEmpty()) {
            boolQueryBuilder.must(m -> m.multiMatch(mm -> mm
                    .query(keyword)
                    .fields("title", "description", "skills")
            ));
        }

        // Location
        if (location != null && !location.trim().isEmpty()) {
            boolQueryBuilder.must(m -> m.match(ma -> ma
                    .field("location")
                    .query(location)
            ));
        }

        // Department (Exact Match)
        if (department != null && !department.trim().isEmpty()) {
            boolQueryBuilder.must(m -> m.term(t -> t.field("department").value(department)));
        }

        Query query = Query.of(q -> q.bool(boolQueryBuilder.build()));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(pageable)
                .build();

        SearchHits<JobDocument> searchHits = elasticsearchOperations.search(nativeQuery, JobDocument.class);
        
        List<JobResponseDto> content = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::mapDocumentToDto)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, searchHits.getTotalHits());
    }

    public Page<JobResponseDto> getEmployerJobs(Long employerId, Pageable pageable) {
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

        boolQueryBuilder.must(m -> m.term(t -> t.field("status").value("PUBLISHED")));
        boolQueryBuilder.must(m -> m.term(t -> t.field("employerId").value(employerId)));

        String nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        boolQueryBuilder.must(m -> m.bool(b -> b
                .should(s -> s.range(r -> r.date(d -> d.field("expiryDate").gt(nowStr))))
                .should(s -> s.bool(b2 -> b2.mustNot(mn -> mn.exists(e -> e.field("expiryDate")))))
                .minimumShouldMatch("1")
        ));

        Query query = Query.of(q -> q.bool(boolQueryBuilder.build()));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(pageable)
                .build();

        SearchHits<JobDocument> searchHits = elasticsearchOperations.search(nativeQuery, JobDocument.class);
        
        List<JobResponseDto> content = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::mapDocumentToDto)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, searchHits.getTotalHits());
    }

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

    public void recordJobView(Long jobId, Long viewerId, String sessionId) {
        // Simple deduplication logic: if same session or viewer viewed within last 30 minutes, skip
        LocalDateTime thirtyMinsAgo = LocalDateTime.now().minusMinutes(30);

        if (viewerId != null) {
            jobViewRepository.findTopByJobIdAndViewerIdOrderByViewedAtDesc(jobId, viewerId)
                    .ifPresent(view -> {
                        if (view.getViewedAt().isAfter(thirtyMinsAgo)) {
                            throw new RateLimitExceededException("Already viewed recently");
                        }
                    });
        } else if (sessionId != null) {
            jobViewRepository.findTopByJobIdAndSessionIdOrderByViewedAtDesc(jobId, sessionId)
                    .ifPresent(view -> {
                        if (view.getViewedAt().isAfter(thirtyMinsAgo)) {
                            throw new RateLimitExceededException("Already viewed recently");
                        }
                    });
        }

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        JobView jobView = JobView.builder()
                .job(job)
                .viewerId(viewerId)
                .sessionId(sessionId)
                .build();
        
        jobViewRepository.save(jobView);
    }

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

    private JobResponseDto mapToDto(Job job) {
        JobResponseDto dto = new JobResponseDto();
        dto.setId(job.getId());
        dto.setTitle(job.getTitle());
        dto.setDescription(job.getDescription());
        dto.setExperience(job.getExperience());
        dto.setSalary(job.getSalary());
        dto.setSkills(job.getSkills());
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
        return dto;
    }
}
