package com.unihire.services.admin;

import com.unihire.dto.EmployerAnalyticsDto;
import com.unihire.models.ApplicationStatus;
import com.unihire.models.JobStatus;
import com.unihire.repositories.JobApplicationRepository;
import com.unihire.repositories.JobRepository;
import com.unihire.repositories.JobViewRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AnalyticsService {

    private final JobRepository jobRepository;
    private final JobApplicationRepository applicationRepository;
    private final JobViewRepository jobViewRepository;

    public AnalyticsService(JobRepository jobRepository,
                            JobApplicationRepository applicationRepository,
                            JobViewRepository jobViewRepository) {
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.jobViewRepository = jobViewRepository;
    }

    /**
     * Returns the aggregated analytics dashboard metrics for an employer.
     * Results are cached for 5 minutes under {@code analytics::employer_analytics_{employerId}}.
     * Cache is evicted when jobs are created/updated/deleted or when application statuses change.
     *
     * @param employerId the employer's user ID
     * @return an {@link EmployerAnalyticsDto} with active jobs, views, applications, hires, and trend data
     */
    @Cacheable(value = "analytics", key = "'employer_analytics_' + #employerId")
    public EmployerAnalyticsDto getEmployerDashboardMetrics(Long employerId) {
        EmployerAnalyticsDto dto = new EmployerAnalyticsDto();

        // Total active jobs
        // Assuming we count PUBLISHED jobs
        long activeJobs = jobRepository.findAll().stream()
                .filter(j -> j.getEmployer().getUser().getId().equals(employerId))
                .filter(j -> j.getStatus() == JobStatus.PUBLISHED)
                .count();
        dto.setTotalActiveJobs(activeJobs);

        // Total views
        long totalViews = jobViewRepository.countByJobEmployerId(employerId);
        dto.setTotalJobViews(totalViews);

        // Total applications
        long totalApps = applicationRepository.findAll().stream()
                .filter(a -> a.getJob().getEmployer().getUser().getId().equals(employerId))
                .count();
        dto.setTotalApplications(totalApps);

        // Total hires
        long totalHires = applicationRepository.findAll().stream()
                .filter(a -> a.getJob().getEmployer().getUser().getId().equals(employerId))
                .filter(a -> a.getStatus() == ApplicationStatus.HIRED)
                .count();
        dto.setTotalHires(totalHires);

        // Conversion Rate
        if (totalViews > 0) {
            dto.setApplicationConversionRate(((double) totalApps / totalViews) * 100.0);
        } else {
            dto.setApplicationConversionRate(0.0);
        }

        // Views over time (last 30 days)
        List<LocalDateTime> viewDates = jobViewRepository.findAllViewDatesByEmployerId(employerId);
        Map<String, Long> viewsOverTime = new TreeMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        // Initialize last 30 days with 0
        LocalDate today = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            viewsOverTime.put(today.minusDays(i).format(formatter), 0L);
        }

        // Populate actual counts
        for (LocalDateTime date : viewDates) {
            String dateString = date.format(formatter);
            if (viewsOverTime.containsKey(dateString)) {
                viewsOverTime.put(dateString, viewsOverTime.get(dateString) + 1);
            }
        }

        dto.setViewsOverTime(viewsOverTime);

        return dto;
    }
}
