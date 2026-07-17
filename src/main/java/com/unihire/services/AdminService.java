package com.unihire.services;

import com.unihire.dto.AdminCandidateDto;
import com.unihire.dto.AdminEmployerDto;
import com.unihire.dto.CandidateDocumentDto;
import com.unihire.dto.LockUserRequest;
import com.unihire.models.*;
import com.unihire.repositories.CandidateDocumentRepository;
import com.unihire.repositories.CandidateProfileRepository;
import com.unihire.repositories.EmployerProfileRepository;
import com.unihire.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final EmployerProfileRepository employerProfileRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateDocumentRepository candidateDocumentRepository;
    private final StorageService storageService;

    public AdminService(UserRepository userRepository, 
                        EmployerProfileRepository employerProfileRepository, 
                        CandidateProfileRepository candidateProfileRepository, 
                        CandidateDocumentRepository candidateDocumentRepository,
                        StorageService storageService) {
                        
        this.userRepository = userRepository;
        this.employerProfileRepository = employerProfileRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.candidateDocumentRepository = candidateDocumentRepository;
        this.storageService = storageService;
    }

    public List<AdminEmployerDto> getAllEmployers() {
        return employerProfileRepository.findAll().stream()
                .map(this::mapToAdminEmployerDto)
                .collect(Collectors.toList());
    }

    public AdminEmployerDto getEmployer(Long id) {
        return employerProfileRepository.findById(id)
                .map(this::mapToAdminEmployerDto)
                .orElseThrow(() -> new RuntimeException("Employer not found"));
    }

    public List<AdminCandidateDto> getAllCandidates() {
        return candidateProfileRepository.findAll().stream()
                .map(this::mapToAdminCandidateDto)
                .collect(Collectors.toList());
    }

    public AdminCandidateDto getCandidate(Long id) {
        return candidateProfileRepository.findById(id)
                .map(this::mapToAdminCandidateDto)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
    }

    @Transactional
    public void toggleUserLock(Long userId, LockUserRequest request, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        user.setIsLocked(!user.getIsLocked());
        if (user.getIsLocked()) {
            user.setLockedAt(LocalDateTime.now());
            user.setLockedBy(adminEmail);
            user.setLockReason(request.getReason());
        } else {
            user.setLockedAt(null);
            user.setLockedBy(null);
            user.setLockReason(null);
        }
        userRepository.save(user);
    }

    @Transactional
    public void updateEmployerVerification(Long employerId, VerificationStatus status) {
        EmployerProfile employer = employerProfileRepository.findById(employerId)
                .orElseThrow(() -> new RuntimeException("Employer not found"));
        employer.setVerificationStatus(status);
        employerProfileRepository.save(employer);
    }

    @Transactional
    public void updateCandidateVerification(Long candidateId, VerificationStatus status) {
        CandidateProfile candidate = candidateProfileRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
        candidate.setVerificationStatus(status);
        candidateProfileRepository.save(candidate);
    }

    @Transactional
    public void verifyDocument(Long documentId, VerificationStatus status, String adminEmail) {
        CandidateDocument doc = candidateDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        doc.setStatus(status);
        if (status == VerificationStatus.VERIFIED || status == VerificationStatus.REJECTED) {
            doc.setVerifiedAt(LocalDateTime.now());
            doc.setVerifiedBy(adminEmail);
        } else {
            doc.setVerifiedAt(null);
            doc.setVerifiedBy(null);
        }
        candidateDocumentRepository.save(doc);
    }

    private AdminEmployerDto mapToAdminEmployerDto(EmployerProfile employer) {
        AdminEmployerDto dto = new AdminEmployerDto();
        dto.setId(employer.getId());
        dto.setEmail(employer.getUser().getEmail());
        dto.setPhone(employer.getUser().getPhone());
        dto.setIsLocked(employer.getUser().getIsLocked());
        dto.setLockedAt(employer.getUser().getLockedAt());
        dto.setLockReason(employer.getUser().getLockReason());
        dto.setCreatedAt(employer.getUser().getCreatedAt());
        dto.setCompanyName(employer.getCompanyName());
        dto.setGstDetails(employer.getGstDetails());
        dto.setWebsite(employer.getWebsite());
        dto.setCompanyProfile(employer.getCompanyProfile());
        dto.setVerificationStatus(employer.getVerificationStatus());
        return dto;
    }

    private AdminCandidateDto mapToAdminCandidateDto(CandidateProfile candidate) {
        AdminCandidateDto dto = new AdminCandidateDto();
        dto.setId(candidate.getId());
        dto.setEmail(candidate.getUser().getEmail());
        dto.setPhone(candidate.getUser().getPhone());
        dto.setIsLocked(candidate.getUser().getIsLocked());
        dto.setLockedAt(candidate.getUser().getLockedAt());
        dto.setLockReason(candidate.getUser().getLockReason());
        dto.setCreatedAt(candidate.getUser().getCreatedAt());
        dto.setFirstName(candidate.getFirstName());
        dto.setLastName(candidate.getLastName());
        dto.setVerificationStatus(candidate.getVerificationStatus());
        
        List<CandidateDocument> docs = candidateDocumentRepository.findByCandidateProfileId(candidate.getId());
        dto.setDocuments(docs.stream().map(doc -> {
            CandidateDocumentDto docDto = new CandidateDocumentDto();
            docDto.setId(doc.getId());
            docDto.setCandidateId(candidate.getId());
            docDto.setDocumentType(doc.getDocumentType());
            docDto.setUrl(storageService.getFileUrl(doc.getObjectKey()));
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
}
