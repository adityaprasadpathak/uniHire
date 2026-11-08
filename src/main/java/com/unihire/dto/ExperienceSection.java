package com.unihire.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceSection {
    private String company;
    private String jobTitle;
    private String startDate;
    private String endDate;
    private java.util.List<String> bullets;
}
