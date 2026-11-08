package com.unihire.controllers;

import com.unihire.dto.ChatMessageDto;
import com.unihire.dto.ChatRequestDto;
import com.unihire.dto.UnreadCountResponseDto;
import com.unihire.models.User;
import com.unihire.services.chat.ChatService;
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

/**
 * Controller for managing real-time STOMP WebSocket chat messages and REST API endpoints
 * for application chat history, unread message metrics, and message status updates.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    /**
     * Constructs a new {@code ChatController} with the required chat service dependency.
     *
     * @param chatService the service executing chat operations and messaging logic
     */
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // --- WebSocket STOMP Endpoint ---

    /**
     * STOMP WebSocket endpoint to process and deliver real-time chat messages.
     * Maps to {@code /chat.sendMessage}.
     *
     * @param chatRequestDto the chat message payload containing the message content and recipient/application details
     * @param authentication the authentication token representing the active sender
     */
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatRequestDto chatRequestDto, Authentication authentication) {
        // authentication contains the UserDetails populated by JwtChannelInterceptor
        String email = authentication.getName(); // Depending on UserDetails config, this is usually email/username
        // For a complete implementation we'd fetch the user by email or inject UserRepository here.
        // I will delegate to ChatService which can take the email string.
        chatService.processMessageByEmail(email, chatRequestDto);
    }

    // --- REST Endpoints ---
    
    /**
     * Retrieves paginated chat history for a specific application.
     *
     * @param applicationId the ID of the application associated with the chat thread
     * @param user the currently authenticated user
     * @param pageable pagination details (page number, size, sort orders)
     * @return a {@link ResponseEntity} containing a {@link Page} of {@link ChatMessageDto} items
     */
    @GetMapping("/{applicationId}/history")
    public ResponseEntity<Page<ChatMessageDto>> getChatHistory(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal User user,
            Pageable pageable) {
        return ResponseEntity.ok(chatService.getChatHistory(applicationId, user.getId(), pageable));
    }

    /**
     * Retrieves the global unread message count for the authenticated user across all applications.
     *
     * @param user the currently authenticated user
     * @return a {@link ResponseEntity} containing a map with key {@code "totalUnread"} and the count
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCounts(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("totalUnread", chatService.getGlobalUnreadCount(user.getId())));
    }

    /**
     * Retrieves a breakdown of unread message counts grouped by application for the authenticated user.
     *
     * @param user the currently authenticated user
     * @return a {@link ResponseEntity} containing a list of {@link UnreadCountResponseDto.ApplicationUnreadDto}
     */
    @GetMapping("/unread-by-application")
    public ResponseEntity<List<UnreadCountResponseDto.ApplicationUnreadDto>> getUnreadByApplication(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(chatService.getUnreadCountsByApplication(user.getId()));
    }

    /**
     * Marks all unread chat messages in the specified application thread as read for the authenticated user.
     *
     * @param applicationId the ID of the application whose chat messages should be marked as read
     * @param user the currently authenticated user
     * @return a {@link ResponseEntity} with HTTP status 204 No Content
     */
    @PutMapping("/{applicationId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long applicationId, @AuthenticationPrincipal User user) {
        chatService.markMessagesAsRead(applicationId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
