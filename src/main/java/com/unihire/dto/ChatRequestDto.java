package com.unihire.dto;

import lombok.Data;

@Data
public class ChatRequestDto {
    private Long applicationId;
    private Long receiverId;
    private String content;
}
