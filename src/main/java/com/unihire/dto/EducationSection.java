package com.unihire.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EducationSection {
    private String degree;
    private String institution;
    private String startDate;
    private String endDate;
    private String description;
}
