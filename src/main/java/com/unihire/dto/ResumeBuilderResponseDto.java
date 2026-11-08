package com.unihire.dto;

import lombok.Data;
import java.util.List;

@Data
public class ResumeBuilderResponseDto {
    private Long candidateId;
    private String compositeProfileJson; // The main profile
    private AtsScoreDto latestAtsScore;
    private List<ResumeVersionDto> savedVersions;
    private ResumeParsingJobDto latestParsingJob;
}
