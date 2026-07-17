package com.unihire.dto;

import lombok.Data;

import java.util.List;

@Data
public class CandidateSummaryDto {
    private Long candidateId;
    private String name;
    private List<String> topSkills;
    private String location; // We don't have location on CandidateProfile yet, but we'll include it if added or omit. Let's omit location for now as it's not in the model.
    private Double similarityScore;
}
