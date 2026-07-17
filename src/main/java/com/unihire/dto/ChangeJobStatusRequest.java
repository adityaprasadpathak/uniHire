package com.unihire.dto;

import lombok.Data;

@Data
public class ChangeJobStatusRequest {
    private String status; // DRAFT, PUBLISHED, CLOSED
}
