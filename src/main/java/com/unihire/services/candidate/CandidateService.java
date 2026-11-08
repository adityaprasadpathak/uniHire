package com.unihire.services.candidate;
import com.unihire.services.storage.StorageService;
import com.unihire.services.search.SemanticSearchService;
import com.unihire.services.resume.ResumeParsingService;
import com.unihire.services.ai.RecommendationService;

import com.unihire.dto.*;
import com.unihire.exceptions.BadRequestException;
import com.unihire.exceptions.ResourceNotFoundException;
import com.unihire.models.*;
import com.unihire.repositories.CandidateDocumentRepository;
import com.unihire.repositories.CandidateProfileRepository;
import com.unihire.repositories.CertificationRepository;
import com.unihire.repositories.EducationRepository;
import com.unihire.repositories.ExperienceRepository;
import com.unihire.utils.DateUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CandidateService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CandidateService.class);

    private final CandidateProfileRepository profileRepository;
    private final EducationRepository educationRepository;
    private final ExperienceRepository experienceRepository;
    private final CertificationRepository certificationRepository;
    private final CandidateDocumentRepository candidateDocumentRepository;
    private final StorageService storageService;
    private final ResumeParsingService parsingService;
    private final SemanticSearchService semanticSearchService;
    private final ApplicationService applicationService;
    private final RecommendationService recommendationService;
    private final com.unihire.repositories.ChatMessageRepository chatMessageRepository;

    public CandidateService(CandidateProfileRepository profileRepository,
                            EducationRepository educationRepository,
                            ExperienceRepository experienceRepository,
                            CertificationRepository certificationRepository,
                            CandidateDocumentRepository candidateDocumentRepository,
                            StorageService storageService,
                            ResumeParsingService parsingService,
                            SemanticSearchService semanticSearchService,
                            ApplicationService applicationService,
                            RecommendationService recommendationService,
                            com.unihire.repositories.ChatMessageRepository chatMessageRepository) {
        this.profileRepository = profileRepository;
        this.educationRepository = educationRepository;
        this.experienceRepository = experienceRepository;
        this.certificationRepository = certificationRepository;
        this.candidateDocumentRepository = candidateDocumentRepository;
        this.storageService = storageService;
        this.parsingService = parsingService;
        this.semanticSearchService = semanticSearchService;
        this.applicationService = applicationService;
        this.recommendationService = recommendationService;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Cacheable(value = "dashboardMetrics", key = "'candidate_' + #userId")
    public CandidateDashboardOverviewDto getDashboardOverview(Long userId) {
        CandidateProfileDto profile = getProfile(userId);
        List<ApplicationResponseDto> recentApplications = applicationService.getCandidateApplications(userId);
        List<RecommendedJobDto> recommendedJobs = recommendationService.getRecommendedJobsForCandidate(userId);
        long unreadCount = chatMessageRepository.countUnreadByReceiver(userId);

        return CandidateDashboardOverviewDto.builder()
                .profile(profile)
                .recentApplications(recentApplications)
                .recommendedJobs(recommendedJobs)
                .unreadMessageCount(unreadCount)
                .build();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "candidateProfiles", key = "'summary_' + #userId")
    public CandidateProfileSummaryDto getProfileSummary(Long userId) {
        CandidateProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        boolean hasResume = profile.getResumeFileKey() != null || (profile.getResumeUrl() != null && !profile.getResumeUrl().isBlank());
        String resumeUrl = null;
        if (profile.getResumeFileKey() != null) {
            resumeUrl = storageService.getFileUrl(profile.getResumeFileKey());
        } else {
            resumeUrl = profile.getResumeUrl();
        }

        return CandidateProfileSummaryDto.builder()
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .profileCompletionScore(profile.getProfileCompletionScore() != null ? profile.getProfileCompletionScore() : 0)
                .hasResume(hasResume)
                .resumeUrl(resumeUrl)
                .build();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "candidateProfiles", key = "#userId")
    public CandidateProfileDto getProfile(Long userId) {
        CandidateProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        CandidateProfileDto dto = new CandidateProfileDto();
        dto.setFirstName(profile.getFirstName());
        dto.setLastName(profile.getLastName());
        dto.setPhone(profile.getPhone());
        dto.setEmail(profile.getUser().getEmail());
        dto.setSkills(profile.getSkills() != null ? new java.util.ArrayList<>(profile.getSkills()) : new java.util.ArrayList<>());
        dto.setLanguages(profile.getLanguages());
        dto.setPortfolioUrl(profile.getPortfolioUrl());
        
        if (profile.getResumeFileKey() != null) {
            dto.setResumeUrl(storageService.getFileUrl(profile.getResumeFileKey()));
        } else {
            dto.setResumeUrl(profile.getResumeUrl());
        }
        
        dto.setParsingStatus(profile.getParsingStatus() != null ? profile.getParsingStatus().name() : null);
        dto.setParsingFailureReason(profile.getParsingFailureReason());
        dto.setProfileCompletionScore(profile.getProfileCompletionScore());

        dto.setEducations(profile.getEducations().stream().map(this::mapToDto).collect(Collectors.toList()));
        dto.setExperiences(profile.getExperiences().stream().map(this::mapToDto).collect(Collectors.toList()));
        dto.setCertifications(profile.getCertifications().stream().map(this::mapToDto).collect(Collectors.toList()));
        
        dto.setVerificationStatus(profile.getVerificationStatus());
        
        List<CandidateDocument> docs = candidateDocumentRepository.findByCandidateProfileId(userId);
        dto.setDocuments(docs.stream().map(doc -> {
            CandidateDocumentDto docDto = new CandidateDocumentDto();
            docDto.setId(doc.getId());
            docDto.setCandidateId(userId);
            docDto.setDocumentType(doc.getDocumentType());
            docDto.setUrl(storageService.getFileUrl(doc.getObjectKey())); // Dynamically generate presigned URL to prevent expiration
            docDto.setOriginalFilename(doc.getOriginalFilename());
            docDto.setContentType(doc.getContentType());
            docDto.setFileSize(doc.getFileSize());
            docDto.setStatus(doc.getStatus());
            docDto.setUploadedAt(doc.getUploadedAt());
            docDto.setVerifiedAt(doc.getVerifiedAt());
            docDto.setVerifiedBy(doc.getVerifiedBy());
            return docDto;
        }).collect(Collectors.toList()));

        return dto;
    }

    public Map<String, String> getParsingStatus(Long userId) {
        CandidateProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        return Map.of("status", profile.getParsingStatus() != null ? profile.getParsingStatus().name() : "NONE");
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "candidateProfiles", key = "#userId", beforeInvocation = true),
        @CacheEvict(value = "candidateProfiles", key = "'summary_' + #userId", beforeInvocation = true),
        @CacheEvict(value = "dashboardMetrics", key = "'candidate_' + #userId", beforeInvocation = true),
        @CacheEvict(value = "recommendations", key = "'jobs_for_candidate_' + #userId", beforeInvocation = true)
    })
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        CandidateProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        if (request.getFirstName() != null) profile.setFirstName(request.getFirstName());
        if (request.getLastName() != null) profile.setLastName(request.getLastName());
        if (request.getPhone() != null) profile.setPhone(request.getPhone());
        if (request.getSkills() != null) profile.setSkills(request.getSkills());
        if (request.getLanguages() != null) profile.setLanguages(request.getLanguages());
        if (request.getPortfolioUrl() != null) profile.setPortfolioUrl(request.getPortfolioUrl());

        profileRepository.save(profile);
        semanticSearchService.indexCandidateProfileAsync(userId);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "candidateProfiles", key = "#userId", beforeInvocation = true),
        @CacheEvict(value = "candidateProfiles", key = "'summary_' + #userId", beforeInvocation = true),
        @CacheEvict(value = "dashboardMetrics", key = "'candidate_' + #userId", beforeInvocation = true),
        @CacheEvict(value = "recommendations", key = "'jobs_for_candidate_' + #userId", beforeInvocation = true)
    })
    public void addEducation(Long userId, EducationDto request) {
        CandidateProfile profile = profileRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        Education education = Education.builder()
                .candidateProfile(profile)
                .degree(request.getDegree())
                .institution(request.getInstitution())
                .startDate(DateUtils.formatMonthYear(request.getStartDate()))
                .endDate(DateUtils.formatMonthYear(request.getEndDate()))
                .description(request.getDescription())
                .build();
        educationRepository.save(education);
        semanticSearchService.indexCandidateProfileAsync(userId);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "candidateProfiles", key = "#userId", beforeInvocation = true),
        @CacheEvict(value = "candidateProfiles", key = "'summary_' + #userId", beforeInvocation = true),
        @CacheEvict(value = "dashboardMetrics", key = "'candidate_' + #userId", beforeInvocation = true),
        @CacheEvict(value = "recommendations", key = "'jobs_for_candidate_' + #userId", beforeInvocation = true)
    })
    public void addExperience(Long userId, ExperienceDto request) {
        CandidateProfile profile = profileRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        Experience exp = Experience.builder()
                .candidateProfile(profile)
                .jobTitle(request.getJobTitle())
                .company(request.getCompany())
                .startDate(DateUtils.formatMonthYear(request.getStartDate()))
                .endDate(DateUtils.formatMonthYear(request.getEndDate()))
                .description(request.getDescription())
                .build();
        experienceRepository.save(exp);
        semanticSearchService.indexCandidateProfileAsync(userId);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "candidateProfiles", key = "#userId", beforeInvocation = true),
        @CacheEvict(value = "candidateProfiles", key = "'summary_' + #userId", beforeInvocation = true),
        @CacheEvict(value = "dashboardMetrics", key = "'candidate_' + #userId", beforeInvocation = true),
        @CacheEvict(value = "recommendations", key = "'jobs_for_candidate_' + #userId", beforeInvocation = true)
    })
    public void addCertification(Long userId, CertificationDto request) {
        CandidateProfile profile = profileRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        Certification cert = Certification.builder()
                .candidateProfile(profile)
                .name(request.getName())
                .issuingOrganization(request.getIssuingOrganization())
                .issueDate(request.getIssueDate())
                .credentialUrl(request.getCredentialUrl())
                .build();
        certificationRepository.save(cert);
        semanticSearchService.indexCandidateProfileAsync(userId);
    }

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    private void validateFileUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("File size exceeds maximum permitted limit of 10 MB.");
        }
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "candidateProfiles", key = "#userId", beforeInvocation = true),
        @CacheEvict(value = "candidateProfiles", key = "'summary_' + #userId", beforeInvocation = true),
        @CacheEvict(value = "dashboardMetrics", key = "'candidate_' + #userId", beforeInvocation = true),
        @CacheEvict(value = "recommendations", key = "'jobs_for_candidate_' + #userId", beforeInvocation = true)
    })
    public void uploadResume(Long userId, MultipartFile file) {
        validateFileUpload(file);

        CandidateProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        if (profile.getResumeFileKey() != null) {
            try {
                storageService.deleteFile(profile.getResumeFileKey());
            } catch (Exception e) {
                logger.warn("Failed to delete old resume: {}", e.getMessage());
            }
        }

        StoredObject storedObj = storageService.uploadFile(file, "candidates/" + userId + "/resumes/");
        String filename = storedObj.getObjectKey();

        // We intentionally do not save the presigned URL to the DB as it expires anyway.
        // It is generated dynamically on read.
        profile.setResumeFileKey(filename);
        profile.setParsingStatus(ParsingStatus.PENDING);
        profile.setParsingFailureReason(null);
        profileRepository.save(profile);

        // Trigger asynchronous resume parsing AFTER the transaction commits so DB state is consistent
        triggerAsyncParsing(userId, filename);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "candidateProfiles", key = "#userId", beforeInvocation = true),
        @CacheEvict(value = "candidateProfiles", key = "'summary_' + #userId", beforeInvocation = true),
        @CacheEvict(value = "dashboardMetrics", key = "'candidate_' + #userId", beforeInvocation = true),
        @CacheEvict(value = "recommendations", key = "'jobs_for_candidate_' + #userId", beforeInvocation = true)
    })
    public void reparseResume(Long userId) {
        CandidateProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        if (profile.getResumeFileKey() == null) {
            throw new BadRequestException("No resume file uploaded to parse.");
        }

        // Eagerly evict stale COMPLETED profile so PENDING status is shown immediately.
        // The background job (ResumeParsingService) will evict again when it finishes.
        profile.setParsingStatus(ParsingStatus.PENDING);
        profile.setParsingFailureReason(null);
        profileRepository.save(profile);

        triggerAsyncParsing(userId, profile.getResumeFileKey());
    }

    private void triggerAsyncParsing(Long userId, String filename) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    parsingService.parseResume(userId, filename);
                }
            });
        } else {
            parsingService.parseResume(userId, filename);
        }
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "candidateProfiles", key = "#userId", beforeInvocation = true),
        @CacheEvict(value = "candidateProfiles", key = "'summary_' + #userId", beforeInvocation = true),
        @CacheEvict(value = "dashboardMetrics", key = "'candidate_' + #userId", beforeInvocation = true)
    })
    public void uploadDocument(Long userId, DocumentType type, MultipartFile file) {
        validateFileUpload(file);

        CandidateProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        // Check if a document of this type was already uploaded
        List<CandidateDocument> existingDocs = candidateDocumentRepository.findByCandidateProfileIdAndDocumentType(userId, type);

        // Delete old physical file(s) from S3 storage
        for (CandidateDocument oldDoc : existingDocs) {
            if (oldDoc.getObjectKey() != null) {
                try {
                    storageService.deleteFile(oldDoc.getObjectKey());
                } catch (Exception e) {
                    logger.warn("Failed to delete old document from storage: {}", e.getMessage());
                }
            }
        }

        // Upload new file to S3
        StoredObject storedObj = storageService.uploadFile(file, "candidates/" + userId + "/documents/");

        CandidateDocument docToSave;
        if (!existingDocs.isEmpty()) {
            // Update existing record in candidate_documents table
            docToSave = existingDocs.get(0);
            if (existingDocs.size() > 1) {
                // Clean up any duplicates from earlier uploads
                candidateDocumentRepository.deleteAll(existingDocs.subList(1, existingDocs.size()));
            }
            docToSave.setObjectKey(storedObj.getObjectKey());
            docToSave.setOriginalFilename(storedObj.getOriginalFilename());
            docToSave.setContentType(storedObj.getContentType());
            docToSave.setFileSize(storedObj.getSize());
            docToSave.setStatus(VerificationStatus.PENDING);
            docToSave.setUploadedAt(java.time.LocalDateTime.now());
            docToSave.setVerifiedAt(null);
            docToSave.setVerifiedBy(null);
        } else {
            // Create a new record
            docToSave = CandidateDocument.builder()
                    .candidateProfile(profile)
                    .documentType(type)
                    .objectKey(storedObj.getObjectKey())
                    .originalFilename(storedObj.getOriginalFilename())
                    .contentType(storedObj.getContentType())
                    .fileSize(storedObj.getSize())
                    .status(VerificationStatus.PENDING)
                    .build();
        }

        candidateDocumentRepository.save(docToSave);

        // When a new document is uploaded or replaced, candidate goes into UNDER_REVIEW
        profile.setVerificationStatus(VerificationStatus.UNDER_REVIEW);
        profileRepository.save(profile);
    }

    private EducationDto mapToDto(Education e) {
        EducationDto dto = new EducationDto();
        dto.setId(e.getId());
        dto.setDegree(e.getDegree());
        dto.setInstitution(e.getInstitution());
        dto.setStartDate(e.getStartDate());
        dto.setEndDate(e.getEndDate());
        dto.setDescription(e.getDescription());
        return dto;
    }

    private ExperienceDto mapToDto(Experience e) {
        ExperienceDto dto = new ExperienceDto();
        dto.setId(e.getId());
        dto.setJobTitle(e.getJobTitle());
        dto.setCompany(e.getCompany());
        dto.setStartDate(e.getStartDate());
        dto.setEndDate(e.getEndDate());
        dto.setDescription(e.getDescription());
        return dto;
    }

    private CertificationDto mapToDto(Certification c) {
        CertificationDto dto = new CertificationDto();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setIssuingOrganization(c.getIssuingOrganization());
        dto.setIssueDate(c.getIssueDate());
        dto.setCredentialUrl(c.getCredentialUrl());
        return dto;
    }
}
