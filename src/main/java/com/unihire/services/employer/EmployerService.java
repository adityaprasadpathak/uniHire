package com.unihire.services.employer;

import com.unihire.dto.EmployerProfileDto;
import com.unihire.dto.UpdateEmployerProfileRequest;
import com.unihire.models.EmployerProfile;
import com.unihire.repositories.EmployerProfileRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployerService {

    private final EmployerProfileRepository employerProfileRepository;

    public EmployerService(EmployerProfileRepository employerProfileRepository) {
        this.employerProfileRepository = employerProfileRepository;
    }

    @Cacheable(value = "employerProfiles", key = "#userId")
    public EmployerProfileDto getProfile(Long userId) {
        EmployerProfile profile = employerProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Employer profile not found"));

        EmployerProfileDto dto = new EmployerProfileDto();
        dto.setCompanyName(profile.getCompanyName());
        dto.setWebsite(profile.getWebsite());
        dto.setEmail(profile.getUser().getEmail());
        return dto;
    }

    @Transactional
    @CacheEvict(value = "employerProfiles", key = "#userId", beforeInvocation = true)
    public void updateProfile(Long userId, UpdateEmployerProfileRequest request) {
        EmployerProfile profile = employerProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Employer profile not found"));

        if (request.getCompanyName() != null) profile.setCompanyName(request.getCompanyName());
        if (request.getWebsite() != null) profile.setWebsite(request.getWebsite());

        employerProfileRepository.save(profile);
    }
}
