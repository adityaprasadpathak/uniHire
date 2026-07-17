package com.unihire.dto;

import lombok.Data;
import java.util.Map;

@Data
public class EmployerAnalyticsDto {
    private long totalActiveJobs;
    private long totalJobViews;
    private long totalApplications;
    private long totalHires;
    private double applicationConversionRate;
    private Map<String, Long> viewsOverTime;
}
