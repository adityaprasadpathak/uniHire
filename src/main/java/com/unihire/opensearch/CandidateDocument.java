package com.unihire.opensearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CandidateDocument {

    private String id; // candidateId.toString()
    private Long candidateId;
    private String fullName;
    private String headline;
    private String skills;
    private String experience;
    private String location;
    private String summary;
    private String email;
    private LocalDateTime updatedAt;
}
