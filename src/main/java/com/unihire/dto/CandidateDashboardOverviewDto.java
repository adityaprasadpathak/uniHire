package com.unihire.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateDashboardOverviewDto {
    private CandidateProfileDto profile;
    private List<ApplicationResponseDto> recentApplications;
    private List<RecommendedJobDto> recommendedJobs;
    private long unreadMessageCount;
}
