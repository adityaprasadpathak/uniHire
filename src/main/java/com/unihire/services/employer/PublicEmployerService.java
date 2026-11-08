package com.unihire.services.employer;
import com.unihire.services.job.PublicJobService;

import com.unihire.dto.JobResponseDto;
import com.unihire.dto.PublicEmployerDto;
import com.unihire.models.EmployerProfile;
import com.unihire.repositories.EmployerProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class PublicEmployerService {

    private final EmployerProfileRepository employerProfileRepository;
    private final PublicJobService publicJobService;

    public PublicEmployerService(EmployerProfileRepository employerProfileRepository, PublicJobService publicJobService) {
        this.employerProfileRepository = employerProfileRepository;
        this.publicJobService = publicJobService;
    }

    public PublicEmployerDto getEmployerProfile(Long employerId) {
        EmployerProfile profile = employerProfileRepository.findById(employerId)
                .orElseThrow(() -> new RuntimeException("Employer not found"));

        PublicEmployerDto dto = new PublicEmployerDto();
        dto.setId(profile.getUser().getId());
        dto.setCompanyName(profile.getCompanyName());
        dto.setWebsite(profile.getWebsite());
        return dto;
    }
}
