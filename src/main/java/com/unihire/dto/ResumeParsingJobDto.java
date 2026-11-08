package com.unihire.dto;

import com.unihire.models.ResumeParsingStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ResumeParsingJobDto {
    private Long id;
    private String originalFileName;
    private ResumeParsingStatus status;
    private String errorMessage;
    private LocalDateTime updatedAt;
}
