package com.unihire.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class EducationDto {
    private Long id;
    private String degree;
    private String institution;
    private LocalDate startDate;
    private LocalDate endDate;
    private String description;
}
