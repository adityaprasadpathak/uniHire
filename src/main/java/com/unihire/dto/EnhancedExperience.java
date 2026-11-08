package com.unihire.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedExperience {
    private Long experienceId;
    private String company;
    private String jobTitle;
    private String enhancedBullets; // newline-separated bullets
}
