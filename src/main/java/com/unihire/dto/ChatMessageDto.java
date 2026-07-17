package com.unihire.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageDto {
    private Long id;
    private Long applicationId;
    private Long senderId;
    private Long receiverId;
    private String content;
    private String messageType;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
