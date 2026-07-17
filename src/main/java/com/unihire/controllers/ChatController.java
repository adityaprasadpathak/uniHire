package com.unihire.controllers;

import com.unihire.dto.ChatMessageDto;
import com.unihire.dto.ChatRequestDto;
import com.unihire.dto.UnreadCountResponseDto;
import com.unihire.models.User;
import com.unihire.services.ChatService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // --- WebSocket STOMP Endpoint ---
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatRequestDto chatRequestDto, Authentication authentication) {
        // authentication contains the UserDetails populated by JwtChannelInterceptor
        String email = authentication.getName(); // Depending on UserDetails config, this is usually email/username
        // For a complete implementation we'd fetch the user by email or inject UserRepository here.
        // I will delegate to ChatService which can take the email string.
        chatService.processMessageByEmail(email, chatRequestDto);
    }

    // --- REST Endpoints ---
    
    @GetMapping("/{applicationId}/history")
    public ResponseEntity<Page<ChatMessageDto>> getChatHistory(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal User user,
            Pageable pageable) {
        return ResponseEntity.ok(chatService.getChatHistory(applicationId, user.getId(), pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCounts(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("totalUnread", chatService.getGlobalUnreadCount(user.getId())));
    }

    @GetMapping("/unread-by-application")
    public ResponseEntity<List<UnreadCountResponseDto.ApplicationUnreadDto>> getUnreadByApplication(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(chatService.getUnreadCountsByApplication(user.getId()));
    }

    @PutMapping("/{applicationId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long applicationId, @AuthenticationPrincipal User user) {
        chatService.markMessagesAsRead(applicationId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
