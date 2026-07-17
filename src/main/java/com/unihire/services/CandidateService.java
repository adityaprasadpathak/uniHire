package com.unihire.services;

import com.unihire.dto.*;
import com.unihire.exceptions.BadRequestException;
import com.unihire.exceptions.ResourceNotFoundException;
import com.unihire.models.*;
import com.unihire.repositories.CandidateDocumentRepository;
import com.unihire.repositories.CandidateProfileRepository;
import com.unihire.repositories.CertificationRepository;
import com.unihire.repositories.EducationRepository;
import com.unihire.repositories.ExperienceRepository;
import org.jobrunr.scheduling.BackgroundJob;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public CandidateService(CandidateProfileRepository profileRepository,
                            EducationRepository educationRepository,
                            ExperienceRepository experienceRepository,
                            CertificationRepository certificationRepository,
                            CandidateDocumentRepository candidateDocumentRepository,
                            StorageService storageService,
                            ResumeParsingService parsingService,
                            SemanticSearchService semanticSearchService) {
        this.profileRepository = profileRepository;
        this.educationRepository = educationRepository;
        this.experienceRepository = experienceRepository;
        this.certificationRepository = certificationRepository;
        this.candidateDocumentRepository = candidateDocumentRepository;
        this.storageService = storageService;
        this.parsingService = parsingService;
        this.semanticSearchService = semanticSearchService;
    }

    public CandidateProfileDto getProfile(Long userId) {
        CandidateProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        CandidateProfileDto dto = new CandidateProfileDto();
        dto.setFirstName(profile.getFirstName());
        dto.setLastName(profile.getLastName());
        dto.setPhone(profile.getPhone());
        dto.setEmail(profile.getUser().getEmail());
        dto.setSkills(profile.getSkills());
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
    public void addEducation(Long userId, EducationDto request) {
        CandidateProfile profile = profileRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        Education education = Education.builder()
                .candidateProfile(profile)
                .degree(request.getDegree())
                .institution(request.getInstitution())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .description(request.getDescription())
                .build();
        educationRepository.save(education);
        semanticSearchService.indexCandidateProfileAsync(userId);
    }

    @Transactional
    public void addExperience(Long userId, ExperienceDto request) {
        CandidateProfile profile = profileRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        Experience exp = Experience.builder()
                .candidateProfile(profile)
                .jobTitle(request.getJobTitle())
                .company(request.getCompany())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .description(request.getDescription())
                .build();
        experienceRepository.save(exp);
        semanticSearchService.indexCandidateProfileAsync(userId);
    }

    @Transactional
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

    @Transactional
    public void uploadResume(Long userId, MultipartFile file) {
        CandidateProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        if (profile.getResumeFileKey() != null) {
            try {
                storageService.deleteFile(profile.getResumeFileKey());
            } catch (Exception e) {
                logger.warn("Failed to delete old resume: {}", e.getMessage());
            }
        }

        com.unihire.dto.StoredObject storedObj = storageService.uploadFile(file, "candidates/" + userId + "/resumes/");
        String filename = storedObj.getObjectKey();

        // We intentionally do not save the presigned URL to the DB as it expires anyway.
        // It is generated dynamically on read.
        profile.setResumeFileKey(filename);
        profile.setParsingStatus(ParsingStatus.PENDING);
        profile.setParsingFailureReason(null);
        profileRepository.save(profile);

        // Enqueue background job using JobRunr
        BackgroundJob.enqueue(() -> parsingService.parseResume(userId, filename));
    }

    @Transactional
    public void reparseResume(Long userId) {
        CandidateProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        if (profile.getResumeFileKey() == null) {
            throw new BadRequestException("No resume file uploaded to parse.");
        }

        profile.setParsingStatus(ParsingStatus.PENDING);
        profile.setParsingFailureReason(null);
        profileRepository.save(profile);

        BackgroundJob.enqueue(() -> parsingService.parseResume(userId, profile.getResumeFileKey()));
    }

    @Transactional
    public void uploadDocument(Long userId, DocumentType type, MultipartFile file) {
        CandidateProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        com.unihire.dto.StoredObject storedObj = storageService.uploadFile(file, "candidates/" + userId + "/documents/");
        
        CandidateDocument doc = CandidateDocument.builder()
                .candidateProfile(profile)
                .documentType(type)
                .objectKey(storedObj.getObjectKey())
                .originalFilename(storedObj.getOriginalFilename())
                .contentType(storedObj.getContentType())
                .fileSize(storedObj.getSize())
                .status(VerificationStatus.PENDING)
                .build();
                
        candidateDocumentRepository.save(doc);
        
        // When a new document is uploaded, candidate goes into UNDER_REVIEW
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
