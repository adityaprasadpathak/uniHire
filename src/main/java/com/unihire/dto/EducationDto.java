package com.unihire.dto;

import lombok.Data;


@Data
public class EducationDto {
    private Long id;
    private String degree;
    private String institution;
    private String startDate;
    private String endDate;
    private String description;
}
