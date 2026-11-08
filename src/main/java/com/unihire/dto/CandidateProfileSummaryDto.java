package com.unihire.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateProfileSummaryDto {
    private String firstName;
    private String lastName;
    private Integer profileCompletionScore;
    private boolean hasResume;
    private String resumeUrl;
}
