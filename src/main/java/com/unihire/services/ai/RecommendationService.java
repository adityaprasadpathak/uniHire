package com.unihire.services.ai;
import com.unihire.services.search.SemanticSearchService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihire.dto.RecommendedCandidateDto;
import com.unihire.dto.RecommendedJobDto;
import com.unihire.models.*;
import com.unihire.repositories.CandidateProfileRepository;
import com.unihire.repositories.JobApplicationRepository;
import com.unihire.repositories.JobRepository;
import com.unihire.repositories.RecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private static final Logger logger = LoggerFactory.getLogger(RecommendationService.class);

    // Scoring weights (must sum to 1.0)
    private static final double WEIGHT_REQUIRED_SKILLS    = 0.35;
    private static final double WEIGHT_SECTOR_ALIGNMENT   = 0.20;
    private static final double WEIGHT_EXPERIENCE         = 0.15;
    private static final double WEIGHT_EDUCATION          = 0.10;
    private static final double WEIGHT_CERTIFICATIONS     = 0.05;
    private static final double WEIGHT_PREFERRED_SKILLS   = 0.05;
    private static final double WEIGHT_SEMANTIC           = 0.10;

    private static final Set<String> BANKING_KEYWORDS = Set.of(
            "banking", "bank", "teller", "credit", "loan", "mortgage", "finance", "financial",
            "accounting", "accountant", "wealth", "investment", "audit", "treasury", "tally",
            "chartered accountant", "cpa", "risk analyst", "portfolio manager", "commercial banking",
            "retail banking", "branch manager"
    );

    private static final Set<String> SOFTWARE_KEYWORDS = Set.of(
            "software", "developer", "engineer", "java", "python", "react", "angular", "vue",
            "node", "spring", "backend", "frontend", "fullstack", "full stack", "devops", "cloud",
            "aws", "docker", "kubernetes", "database", "sql", "git", "web", "code", "programmer",
            "system admin", "technology", "it", "microservices"
    );

    private static final java.util.regex.Pattern BANKING_PATTERN = java.util.regex.Pattern.compile(
            "\\b(" + String.join("|", BANKING_KEYWORDS) + ")\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private static final java.util.regex.Pattern SOFTWARE_PATTERN = java.util.regex.Pattern.compile(
            "\\b(" + String.join("|", SOFTWARE_KEYWORDS) + ")\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private final RecommendationRepository recommendationRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final JobRepository jobRepository;
    private final JobApplicationRepository applicationRepository;
    private final SemanticSearchService semanticSearchService;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecommendationService(RecommendationRepository recommendationRepository,
                                  CandidateProfileRepository candidateProfileRepository,
                                  JobRepository jobRepository,
                                  JobApplicationRepository applicationRepository,
                                  SemanticSearchService semanticSearchService,
                                  CacheManager cacheManager) {
        this.recommendationRepository = recommendationRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.semanticSearchService = semanticSearchService;
        this.cacheManager = cacheManager;
    }

    // -------------------------------------------------------------------------
    // EMPLOYER: recommend candidates for a job
    // -------------------------------------------------------------------------

    @Async
    @Transactional
    public void generateRecommendationsForJob(Long jobId) {
        try {
            Job job = jobRepository.findById(jobId).orElse(null);
            if (job == null) return;

            // Clear old recommendations for this job
            recommendationRepository.deleteByJobIdAndType(jobId, RecommendationType.CANDIDATE_FOR_JOB);

            // Parse required & preferred skills from the job (CSV)
            List<String> requiredSkills = parseSkills(job.getSkills());
            List<String> preferredSkills = parseSkills(job.getPreferredSkills());

            // Get semantic search hits for AI score (top 50 to have a wider pool)
            Map<Long, Double> semanticScores = new HashMap<>();
            try {
                String query = buildJobSearchQuery(job);
                semanticSearchService.searchCandidates(query, 50).forEach(dto ->
                    semanticScores.put(dto.getCandidateId(), dto.getSimilarityScore()));
            } catch (Exception e) {
                logger.warn("Semantic search for job {} failed: {}", jobId, e.getMessage());
            }

            // Score all candidates (use a limited set for performance - top 200 by semantic)
            List<Long> candidateIds = semanticScores.isEmpty()
                ? candidateProfileRepository.findAll().stream().map(CandidateProfile::getId).collect(Collectors.toList())
                : new ArrayList<>(semanticScores.keySet());

            List<Recommendation> recommendations = new ArrayList<>();

            for (Long candidateId : candidateIds) {
                CandidateProfile candidate = candidateProfileRepository.findById(candidateId).orElse(null);
                if (candidate == null) continue;

                RecommendedCandidateDto dto = scoreCandidate(candidate, job, requiredSkills, preferredSkills,
                        semanticScores.getOrDefault(candidateId, 0.0));

                if (dto.getMatchScore() >= 40.0) { // Only save meaningful matches
                    Recommendation rec = Recommendation.builder()
                            .job(job)
                            .candidate(candidate)
                            .score(dto.getMatchScore())
                            .reason(toJson(dto))
                            .type(RecommendationType.CANDIDATE_FOR_JOB)
                            .build();
                    recommendations.add(rec);
                }
            }

            // Sort and save top 10
            recommendations.sort(Comparator.comparingDouble(Recommendation::getScore).reversed());
            List<Recommendation> top10 = recommendations.stream().limit(10).collect(Collectors.toList());
            recommendationRepository.saveAll(top10);
            logger.info("Generated {} recommendations for job {}", top10.size(), jobId);

            // Evict the stale cache entry so the next read gets fresh data
            evictRecommendationCache("candidates_for_job_" + jobId);

        } catch (Exception e) {
            logger.error("Failed to generate recommendations for job {}: {}", jobId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // CANDIDATE: recommend jobs for a candidate
    // -------------------------------------------------------------------------

    @Async
    @Transactional
    public void generateRecommendationsForCandidate(Long candidateId) {
        try {
            CandidateProfile candidate = candidateProfileRepository.findById(candidateId).orElse(null);
            if (candidate == null || candidate.getSkills() == null || candidate.getSkills().isEmpty()) return;

            // Clear old recommendations
            recommendationRepository.deleteByCandidateIdAndType(candidateId, RecommendationType.JOB_FOR_CANDIDATE);

            Set<Long> appliedJobIds = applicationRepository.findByCandidateProfileId(candidateId).stream()
                    .map(app -> app.getJob().getId())
                    .collect(Collectors.toSet());

            // Get published jobs (excluding jobs candidate already applied to)
            List<Job> publishedJobs = jobRepository.findAllEager().stream()
                    .filter(j -> j.getStatus() == JobStatus.PUBLISHED && !appliedJobIds.contains(j.getId()))
                    .collect(Collectors.toList());

            List<Recommendation> recommendations = new ArrayList<>();

            for (Job job : publishedJobs) {
                List<String> requiredSkills = parseSkills(job.getSkills());
                List<String> preferredSkills = parseSkills(job.getPreferredSkills());

                RecommendedJobDto dto = scoreJob(job, candidate, requiredSkills, preferredSkills);

                if (dto.getMatchScore() >= 40.0) {
                    Recommendation rec = Recommendation.builder()
                            .job(job)
                            .candidate(candidate)
                            .score(dto.getMatchScore())
                            .reason(toJson(dto))
                            .type(RecommendationType.JOB_FOR_CANDIDATE)
                            .build();
                    recommendations.add(rec);
                }
            }

            recommendations.sort(Comparator.comparingDouble(Recommendation::getScore).reversed());
            List<Recommendation> top10 = recommendations.stream().limit(10).collect(Collectors.toList());
            recommendationRepository.saveAll(top10);
            logger.info("Generated {} job recommendations for candidate {}", top10.size(), candidateId);

            // Evict the stale cache entry so the next read gets fresh data
            evictRecommendationCache("jobs_for_candidate_" + candidateId);

        } catch (Exception e) {
            logger.error("Failed to generate job recommendations for candidate {}: {}", candidateId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // READ: get recommendations
    // -------------------------------------------------------------------------

    /**
     * Returns the top 10 recommended candidates for a given job.
     * Results are cached under {@code recommendations::candidates_for_job_{jobId}}.
     * Cache is evicted when recommendations are regenerated or when the job is updated/deleted.
     *
     * @param jobId the ID of the job
     * @return list of up to 10 recommended candidates with match scores
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "recommendations", key = "'candidates_for_job_' + #jobId")
    public List<RecommendedCandidateDto> getRecommendedCandidatesForJob(Long jobId) {
        List<Recommendation> recs = recommendationRepository
                .findByJobIdAndTypeOrderByScoreDesc(jobId, RecommendationType.CANDIDATE_FOR_JOB, PageRequest.of(0, 10));

        if (recs.isEmpty()) {
            // Trigger async generation and return empty immediately
            generateRecommendationsForJob(jobId);
            return List.of();
        }
        return recs.stream().map(r -> fromJson(r.getReason(), RecommendedCandidateDto.class))
                .filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * Returns the top 10 recommended jobs for a given candidate.
     * Results are cached under {@code recommendations::jobs_for_candidate_{candidateId}}.
     * Cache is evicted when the candidate profile changes or when the candidate applies to a job.
     *
     * @param candidateId the ID of the candidate
     * @return list of up to 10 recommended jobs with match scores (already-applied jobs filtered out)
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "recommendations", key = "'jobs_for_candidate_' + #candidateId")
    public List<RecommendedJobDto> getRecommendedJobsForCandidate(Long candidateId) {
        Set<Long> appliedJobIds = applicationRepository.findByCandidateProfileId(candidateId).stream()
                .map(app -> app.getJob().getId())
                .collect(Collectors.toSet());

        List<Recommendation> recs = recommendationRepository
                .findByCandidateIdAndTypeOrderByScoreDesc(candidateId, RecommendationType.JOB_FOR_CANDIDATE, PageRequest.of(0, 20));

        if (recs.isEmpty()) {
            generateRecommendationsForCandidate(candidateId);
            return List.of();
        }
        return recs.stream()
                .map(r -> fromJson(r.getReason(), RecommendedJobDto.class))
                .filter(Objects::nonNull)
                .filter(dto -> !appliedJobIds.contains(dto.getJobId()))
                .limit(10)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // SCORING LOGIC
    // -------------------------------------------------------------------------

    private RecommendedCandidateDto scoreCandidate(CandidateProfile candidate, Job job,
                                                    List<String> requiredSkills, List<String> preferredSkills,
                                                    double semanticScore) {
        List<String> candidateSkills = candidate.getSkills() != null ? candidate.getSkills() : List.of();
        List<String> candidateSkillsLower = candidateSkills.stream().map(String::toLowerCase).collect(Collectors.toList());

        // --- Skills Score (40%) ---
        List<String> matchedRequired = new ArrayList<>();
        List<String> missingRequired = new ArrayList<>();
        for (String skill : requiredSkills) {
            if (candidateSkillsLower.stream().anyMatch(cs -> cs.contains(skill.toLowerCase()) || skill.toLowerCase().contains(cs))) {
                matchedRequired.add(skill);
            } else {
                missingRequired.add(skill);
            }
        }
        double skillsScore = requiredSkills.isEmpty() ? 1.0 : (double) matchedRequired.size() / requiredSkills.size();

        // --- Preferred Skills Score (10%) ---
        List<String> matchedPreferred = new ArrayList<>();
        for (String skill : preferredSkills) {
            if (candidateSkillsLower.stream().anyMatch(cs -> cs.contains(skill.toLowerCase()) || skill.toLowerCase().contains(cs))) {
                matchedPreferred.add(skill);
            }
        }
        double preferredScore = preferredSkills.isEmpty() ? 1.0 : (double) matchedPreferred.size() / preferredSkills.size();

        // --- Experience Score (20%) ---
        int candidateYears = calculateTotalExperienceYears(candidate);
        double experienceScore = calculateExperienceScore(candidateYears, job.getMinExperience(), job.getMaxExperience());

        // --- Education Score (10%) ---
        double educationScore = calculateEducationScore(candidate.getEducations(), job.getEducation());

        // --- Certification Score (10%) ---
        double certScore = calculateCertificationScore(candidate.getCertifications(), job.getCertifications());
        double sectorScore = calculateSectorAlignment(candidate, job);

        // --- Semantic AI Score (10%) ---
        // semanticScore is 0.0 to 1.0
        double normSemantic = Math.min(1.0, Math.max(0.0, semanticScore));

        double totalScore = (skillsScore * WEIGHT_REQUIRED_SKILLS
                + sectorScore * WEIGHT_SECTOR_ALIGNMENT
                + experienceScore * WEIGHT_EXPERIENCE
                + educationScore * WEIGHT_EDUCATION
                + certScore * WEIGHT_CERTIFICATIONS
                + preferredScore * WEIGHT_PREFERRED_SKILLS
                + normSemantic * WEIGHT_SEMANTIC) * 100.0;

        if (sectorScore < 0.2) {
            totalScore *= 0.1; // Heavy penalty for cross-sector mismatch (e.g. Banking vs Software)
        }
        
        if (!requiredSkills.isEmpty() && matchedRequired.isEmpty()) {
            totalScore *= 0.3; // Penalty for having zero matching required skills
        }

        // Combine all matched skills
        List<String> allMatched = new ArrayList<>(matchedRequired);
        allMatched.addAll(matchedPreferred);

        String candidateName = ((candidate.getFirstName() != null ? candidate.getFirstName() : "") + " " +
                (candidate.getLastName() != null ? candidate.getLastName() : "")).trim();

        String reason = buildCandidateReason(matchedRequired, missingRequired, candidateYears, job.getMinExperience());

        return RecommendedCandidateDto.builder()
                .candidateId(candidate.getId())
                .name(candidateName)
                .matchScore(Math.round(totalScore * 10.0) / 10.0)
                .matchedSkills(allMatched)
                .missingSkills(missingRequired)
                .experienceYears(candidateYears)
                .highestDegree(getHighestDegree(candidate.getEducations()))
                .certifications(candidate.getCertifications() != null
                        ? candidate.getCertifications().stream().map(Certification::getName).collect(Collectors.toList())
                        : List.of())
                .reason(reason)
                .build();
    }

    private RecommendedJobDto scoreJob(Job job, CandidateProfile candidate,
                                        List<String> requiredSkills, List<String> preferredSkills) {
        List<String> candidateSkills = candidate.getSkills() != null ? candidate.getSkills() : List.of();
        List<String> candidateSkillsLower = candidateSkills.stream().map(String::toLowerCase).collect(Collectors.toList());

        // Skills match
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String skill : requiredSkills) {
            if (candidateSkillsLower.stream().anyMatch(cs -> cs.contains(skill.toLowerCase()) || skill.toLowerCase().contains(cs))) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }
        double skillsScore = requiredSkills.isEmpty() ? 1.0 : (double) matched.size() / requiredSkills.size();

        List<String> matchedPreferred = new ArrayList<>();
        for (String skill : preferredSkills) {
            if (candidateSkillsLower.stream().anyMatch(cs -> cs.contains(skill.toLowerCase()) || skill.toLowerCase().contains(cs))) {
                matchedPreferred.add(skill);
            }
        }
        double preferredScore = preferredSkills.isEmpty() ? 1.0 : (double) matchedPreferred.size() / preferredSkills.size();

        int candidateYears = calculateTotalExperienceYears(candidate);
        double experienceScore = calculateExperienceScore(candidateYears, job.getMinExperience(), job.getMaxExperience());
        double educationScore = calculateEducationScore(candidate.getEducations(), job.getEducation());
        double certScore = calculateCertificationScore(candidate.getCertifications(), job.getCertifications());
        double sectorScore = calculateSectorAlignment(candidate, job);

        double totalScore = (skillsScore * WEIGHT_REQUIRED_SKILLS
                + sectorScore * WEIGHT_SECTOR_ALIGNMENT
                + experienceScore * WEIGHT_EXPERIENCE
                + educationScore * WEIGHT_EDUCATION
                + certScore * WEIGHT_CERTIFICATIONS
                + preferredScore * WEIGHT_PREFERRED_SKILLS
                + 0.5 * WEIGHT_SEMANTIC) * 100.0;

        if (sectorScore < 0.2) {
            totalScore *= 0.1; // Heavy penalty for cross-sector mismatch (e.g. Banking vs Software)
        }

        if (!requiredSkills.isEmpty() && matched.isEmpty()) {
            totalScore *= 0.3; // Penalty for having zero matching required skills
        }

        List<String> allMatched = new ArrayList<>(matched);
        allMatched.addAll(matchedPreferred);

        String reason = buildJobReason(matched, missing, candidateYears, job.getMinExperience());

        String companyName = (job.getEmployer() != null)
                ? job.getEmployer().getCompanyName() : "";

        return RecommendedJobDto.builder()
                .jobId(job.getId())
                .title(job.getTitle())
                .companyName(companyName)
                .location(job.getLocation())
                .employmentType(job.getEmploymentType())
                .salary(job.getSalary())
                .matchScore(Math.round(totalScore * 10.0) / 10.0)
                .matchedSkills(allMatched)
                .missingSkills(missing)
                .reason(reason)
                .build();
    }

    // -------------------------------------------------------------------------
    // HELPER METHODS
    // -------------------------------------------------------------------------

    private List<String> parseSkills(String skillsCsv) {
        if (skillsCsv == null || skillsCsv.isBlank()) return List.of();
        return Arrays.stream(skillsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private int calculateTotalExperienceYears(CandidateProfile candidate) {
        if (candidate.getExperiences() == null || candidate.getExperiences().isEmpty()) return 0;
        long totalMonths = 0;
        for (Experience exp : candidate.getExperiences()) {
            LocalDate start = parseEstimatedDate(exp.getStartDate());
            LocalDate end = parseEstimatedDate(exp.getEndDate());
            if (start != null && end != null && end.isAfter(start)) {
                totalMonths += ChronoUnit.MONTHS.between(start, end);
            }
        }
        return (int) (totalMonths / 12);
    }

    private LocalDate parseEstimatedDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || dateStr.equalsIgnoreCase("present") || dateStr.equalsIgnoreCase("current")) {
            return LocalDate.now();
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(".*(19\\d{2}|20\\d{2}).*").matcher(dateStr);
        if (m.matches()) {
            int year = Integer.parseInt(m.group(1));
            return LocalDate.of(year, 1, 1);
        }
        return LocalDate.now();
    }

    private double calculateExperienceScore(int candidateYears, Integer minExp, Integer maxExp) {
        if (minExp == null && maxExp == null) return 1.0; // No experience requirement
        int min = minExp != null ? minExp : 0;
        if (candidateYears >= min && (maxExp == null || candidateYears <= maxExp + 2)) return 1.0;
        if (candidateYears < min) {
            double ratio = (double) candidateYears / min;
            return Math.max(0, ratio); // Partial credit
        }
        return 0.8; // Over-experienced - still good
    }

    private double calculateEducationScore(List<Education> educations, String jobEducation) {
        if (jobEducation == null || jobEducation.isBlank()) return 1.0;
        if (educations == null || educations.isEmpty()) return 0.3;
        String jobEduLower = jobEducation.toLowerCase();
        for (Education edu : educations) {
            String degree = edu.getDegree() != null ? edu.getDegree().toLowerCase() : "";
            if (degree.contains("phd") || degree.contains("doctorate")) return 1.0;
            if (degree.contains("master") || degree.contains("mba") || degree.contains("mtech")) {
                if (jobEduLower.contains("bachelor") || jobEduLower.contains("graduate")) return 1.0;
                return 0.9;
            }
            if (degree.contains("bachelor") || degree.contains("btech") || degree.contains("be") || degree.contains("bsc")) {
                if (jobEduLower.contains("bachelor") || jobEduLower.contains("graduate")) return 1.0;
                if (jobEduLower.contains("master")) return 0.6;
                return 0.8;
            }
        }
        return 0.5;
    }

    private double calculateCertificationScore(List<Certification> certifications, String jobCertifications) {
        if (jobCertifications == null || jobCertifications.isBlank()) return 1.0;
        if (certifications == null || certifications.isEmpty()) return 0.3;
        List<String> jobCerts = parseSkills(jobCertifications);
        long matched = certifications.stream()
                .filter(c -> jobCerts.stream().anyMatch(jc -> c.getName().toLowerCase().contains(jc.toLowerCase())))
                .count();
        return jobCerts.isEmpty() ? 1.0 : Math.min(1.0, (double) matched / jobCerts.size());
    }

    private String getHighestDegree(List<Education> educations) {
        if (educations == null || educations.isEmpty()) return null;
        return educations.stream().findFirst().map(Education::getDegree).orElse(null);
    }

    private String buildJobSearchQuery(Job job) {
        StringBuilder sb = new StringBuilder();
        sb.append(job.getTitle()).append(" ");
        if (job.getSkills() != null) sb.append(job.getSkills()).append(" ");
        if (job.getDescription() != null) sb.append(job.getDescription(), 0, Math.min(200, job.getDescription().length()));
        return sb.toString().trim();
    }

    private String buildCandidateReason(List<String> matched, List<String> missing, int years, Integer minExp) {
        StringBuilder sb = new StringBuilder();
        matched.stream().limit(5).forEach(s -> sb.append("✓ ").append(s).append(", "));
        if (years > 0) sb.append("✓ ").append(years).append(" yrs experience");
        if (!missing.isEmpty()) sb.append(" | Missing: ").append(missing.stream().limit(3).collect(Collectors.joining(", ")));
        return sb.toString().replaceAll(",\\s*$", "").trim();
    }

    private String buildJobReason(List<String> matched, List<String> missing, int years, Integer minExp) {
        StringBuilder sb = new StringBuilder();
        matched.stream().limit(5).forEach(s -> sb.append("Matches your ").append(s).append(" experience. "));
        if (!missing.isEmpty()) sb.append("Requires: ").append(missing.stream().limit(3).collect(Collectors.joining(", ")));
        return sb.toString().trim();
    }

    private double calculateSectorAlignment(CandidateProfile candidate, Job job) {
        String jobText = buildJobText(job).toLowerCase();
        String candidateText = buildCandidateText(candidate).toLowerCase();

        boolean isJobBanking = BANKING_PATTERN.matcher(jobText).find();
        boolean isJobSoftware = SOFTWARE_PATTERN.matcher(jobText).find();

        boolean isCandidateBanking = BANKING_PATTERN.matcher(candidateText).find();
        boolean isCandidateSoftware = SOFTWARE_PATTERN.matcher(candidateText).find();

        // FinTech or Hybrid role (e.g. Software Engineer in Banking domain)
        if (isJobBanking && isJobSoftware) {
            return 0.9;
        }

        // Direct domain match
        if (isJobBanking && isCandidateBanking) {
            return 1.0;
        }
        if (isJobSoftware && isCandidateSoftware) {
            return 1.0;
        }

        // Severe Cross-sector mismatch (e.g. Banking Candidate applying for pure Software Job or vice-versa)
        if (isJobSoftware && isCandidateBanking && !isCandidateSoftware) {
            return 0.05;
        }
        if (isJobBanking && isCandidateSoftware && !isCandidateBanking) {
            return 0.05;
        }

        // Neutral / Default domain alignment
        return 0.85;
    }

    private String buildJobText(Job job) {
        StringBuilder sb = new StringBuilder();
        if (job.getTitle() != null) sb.append(job.getTitle()).append(" ");
        if (job.getIndustry() != null) sb.append(job.getIndustry()).append(" ");
        if (job.getDepartment() != null) sb.append(job.getDepartment()).append(" ");
        if (job.getSkills() != null) sb.append(job.getSkills()).append(" ");
        if (job.getDescription() != null) sb.append(job.getDescription()).append(" ");
        return sb.toString();
    }

    private String buildCandidateText(CandidateProfile candidate) {
        StringBuilder sb = new StringBuilder();
        if (candidate.getSkills() != null) {
            sb.append(String.join(" ", candidate.getSkills())).append(" ");
        }
        if (candidate.getExperiences() != null) {
            for (Experience exp : candidate.getExperiences()) {
                if (exp.getJobTitle() != null) sb.append(exp.getJobTitle()).append(" ");
                if (exp.getCompany() != null) sb.append(exp.getCompany()).append(" ");
                if (exp.getDescription() != null) sb.append(exp.getDescription()).append(" ");
            }
        }
        if (candidate.getEducations() != null) {
            for (Education edu : candidate.getEducations()) {
                if (edu.getDegree() != null) sb.append(edu.getDegree()).append(" ");
                if (edu.getInstitution() != null) sb.append(edu.getInstitution()).append(" ");
            }
        }
        return sb.toString();
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return "{}"; }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try { return objectMapper.readValue(json, clazz); }
        catch (Exception e) { return null; }
    }

    /**
     * Programmatically evicts a specific entry from the {@code recommendations} cache.
     * Used inside {@code @Async} methods where {@code @CacheEvict} annotations are not proxied.
     *
     * @param cacheKey the key to evict (e.g., "candidates_for_job_42")
     */
    private void evictRecommendationCache(String cacheKey) {
        try {
            var cache = cacheManager.getCache("recommendations");
            if (cache != null) {
                cache.evict(cacheKey);
                logger.debug("Evicted recommendations cache key: {}", cacheKey);
            }
        } catch (Exception e) {
            logger.warn("Failed to evict recommendations cache key {}: {}", cacheKey, e.getMessage());
        }
    }
}
