package com.unihire.services;

import com.unihire.dto.ChatMessageDto;
import com.unihire.dto.ChatRequestDto;
import com.unihire.dto.UnreadCountResponseDto;
import com.unihire.models.ChatMessage;
import com.unihire.models.JobApplication;
import com.unihire.exceptions.ResourceNotFoundException;
import com.unihire.exceptions.UnauthorizedOperationException;
import com.unihire.models.User;
import com.unihire.repositories.ChatMessageRepository;
import com.unihire.repositories.JobApplicationRepository;
import com.unihire.repositories.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

@Service
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatService(ChatMessageRepository chatMessageRepository,
                       JobApplicationRepository jobApplicationRepository,
                       UserRepository userRepository,
                       SimpMessagingTemplate messagingTemplate) {
        this.chatMessageRepository = chatMessageRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public ChatMessageDto processMessageByEmail(String email, ChatRequestDto request) {
        User sender = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));
        return processMessage(sender.getId(), request);
    }

    @Transactional
    public ChatMessageDto processMessage(Long senderId, ChatRequestDto request) {
        JobApplication application = jobApplicationRepository.findById(request.getApplicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));
        // Validate that sender is either the candidate or the employer for this application
        Long candidateUserId = application.getCandidateProfile().getUser().getId();
        Long employerUserId = application.getJob().getEmployer().getUser().getId();
        
        if (!senderId.equals(candidateUserId) && !senderId.equals(employerUserId)) {
            throw new UnauthorizedOperationException("Unauthorized to send messages for this application");
        }

        User receiver;
        if (senderId.equals(candidateUserId)) {
            receiver = application.getJob().getEmployer().getUser();
        } else {
            receiver = application.getCandidateProfile().getUser();
        }

        ChatMessage message = ChatMessage.builder()
                .application(application)
                .sender(sender)
                .receiver(receiver)
                .content(request.getContent())
                .messageType("TEXT")
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(message);
        ChatMessageDto dto = mapToDto(savedMessage);

        // Send to receiver via WebSocket
        messagingTemplate.convertAndSendToUser(
                receiver.getEmail(),
                "/queue/messages",
                dto
        );
        
        // Fire Unread Count Changed Event for the receiver
        long updatedTotal = chatMessageRepository.countUnreadByReceiver(receiver.getId());
        Map<String, Object> wsEvent = new HashMap<>();
        wsEvent.put("type", "UNREAD_COUNT_CHANGED");
        wsEvent.put("applicationId", application.getId());
        wsEvent.put("totalUnread", updatedTotal);
        messagingTemplate.convertAndSendToUser(
                receiver.getEmail(),
                "/queue/notifications",
                wsEvent
        );
        
        // Echo to sender so UI updates
        messagingTemplate.convertAndSendToUser(
                sender.getEmail(),
                "/queue/messages",
                dto
        );

        return dto;
    }

    public Page<ChatMessageDto> getChatHistory(Long applicationId, Long userId, Pageable pageable) {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        Long candidateUserId = application.getCandidateProfile().getUser().getId();
        Long employerUserId = application.getJob().getEmployer().getUser().getId();

        if (!userId.equals(candidateUserId) && !userId.equals(employerUserId)) {
            throw new UnauthorizedOperationException("Unauthorized to view this application's chat");
        }

        return chatMessageRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId, pageable)
                .map(this::mapToDto);
    }

    public long getGlobalUnreadCount(Long userId) {
        return chatMessageRepository.countUnreadByReceiver(userId);
    }

    public List<UnreadCountResponseDto.ApplicationUnreadDto> getUnreadCountsByApplication(Long userId) {
        List<Object[]> results = chatMessageRepository.countUnreadByReceiverGroupedByApplication(userId);
        return results.stream().map(result -> {
            UnreadCountResponseDto.ApplicationUnreadDto dto = new UnreadCountResponseDto.ApplicationUnreadDto();
            dto.setApplicationId(((Number) result[0]).longValue());
            dto.setUnread(((Number) result[1]).longValue());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void markMessagesAsRead(Long applicationId, Long receiverId) {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));
        User user = userRepository.findById(receiverId)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        chatMessageRepository.markAsRead(applicationId, receiverId);
        
        // Fire WebSocket event to clear unread counts on UI
        long updatedTotal = chatMessageRepository.countUnreadByReceiver(receiverId);
        Map<String, Object> wsEvent = new HashMap<>();
        wsEvent.put("type", "UNREAD_COUNT_CHANGED");
        wsEvent.put("applicationId", applicationId);
        wsEvent.put("totalUnread", updatedTotal);
        messagingTemplate.convertAndSendToUser(
                user.getEmail(),
                "/queue/notifications",
                wsEvent
        );
    }

    private ChatMessageDto mapToDto(ChatMessage message) {
        ChatMessageDto dto = new ChatMessageDto();
        dto.setId(message.getId());
        dto.setApplicationId(message.getApplication().getId());
        dto.setSenderId(message.getSender().getId());
        dto.setReceiverId(message.getReceiver().getId());
        dto.setContent(message.getContent());
        dto.setMessageType(message.getMessageType());
        dto.setCreatedAt(message.getCreatedAt());
        dto.setReadAt(message.getReadAt());
        return dto;
    }
}
