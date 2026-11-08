package com.unihire.services.resume;

import com.unihire.dto.AtsScoreDto;
import org.springframework.stereotype.Service;

@Service
public class AtsScoringEngine {

    public AtsScoreDto calculateAtsScore(String resumeText, String targetJd) {
        // Implementation of the ATS Scoring Model
        // ATS Score = (0.30 x Keywords) + (0.25 x Skills) + (0.20 x Experience) + (0.15 x Formatting) + (0.10 x Role Alignment)
        
        // Mock scoring logic for now
        int keywordScore = 85; 
        int skillsScore = 80;
        int experienceScore = 90;
        int formattingScore = 70;
        int roleAlignmentScore = 80;

        int totalScore = (int) ((0.30 * keywordScore) + (0.25 * skillsScore) + (0.20 * experienceScore) + (0.15 * formattingScore) + (0.10 * roleAlignmentScore));

        return AtsScoreDto.builder()
                .totalScore(totalScore)
                .keywordScore(keywordScore)
                .skillsScore(skillsScore)
                .experienceScore(experienceScore)
                .formattingScore(formattingScore)
                .roleAlignmentScore(roleAlignmentScore)
                .feedback("Resume is highly aligned. Consider adding more measurable impact metrics to improve formatting score.")
                .build();
    }
}
