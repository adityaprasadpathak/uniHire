package com.unihire.dto;

import lombok.Data;
import java.util.List;

@Data
public class UnreadCountResponseDto {
    private Long totalUnread;
    private List<ApplicationUnreadDto> applications;

    @Data
    public static class ApplicationUnreadDto {
        private Long applicationId;
        private Long unread;
    }
}
