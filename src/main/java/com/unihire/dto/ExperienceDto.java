package com.unihire.dto;

import lombok.Data;


@Data
public class ExperienceDto {
    private Long id;
    private String jobTitle;
    private String company;
    private String startDate;
    private String endDate;
    private String description;
}
