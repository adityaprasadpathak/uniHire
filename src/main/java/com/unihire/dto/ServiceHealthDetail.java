package com.unihire.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceHealthDetail {
    private String status;
    private Long latencyMs;
    private String details;
    private String error;

    public static ServiceHealthDetail up(long latencyMs, String details) {
        return ServiceHealthDetail.builder()
                .status("UP")
                .latencyMs(latencyMs)
                .details(details)
                .build();
    }

    public static ServiceHealthDetail down(long latencyMs, String error) {
        return ServiceHealthDetail.builder()
                .status("DOWN")
                .latencyMs(latencyMs)
                .error(error)
                .build();
    }
}
