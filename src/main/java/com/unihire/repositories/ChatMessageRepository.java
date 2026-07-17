package com.unihire.repositories;

import com.unihire.models.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    
    Page<ChatMessage> findByApplicationIdOrderByCreatedAtDesc(Long applicationId, Pageable pageable);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.application.id = :applicationId AND m.receiver.id = :receiverId AND m.readAt IS NULL")
    long countUnreadByApplicationAndReceiver(@Param("applicationId") Long applicationId, @Param("receiverId") Long receiverId);
    
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.receiver.id = :receiverId AND m.readAt IS NULL")
    long countUnreadByReceiver(@Param("receiverId") Long receiverId);

    @Query("SELECT m.application.id, COUNT(m) FROM ChatMessage m WHERE m.receiver.id = :receiverId AND m.readAt IS NULL GROUP BY m.application.id")
    List<Object[]> countUnreadByReceiverGroupedByApplication(@Param("receiverId") Long receiverId);
    
    @Modifying
    @Query("UPDATE ChatMessage m SET m.readAt = CURRENT_TIMESTAMP WHERE m.application.id = :applicationId AND m.receiver.id = :receiverId AND m.readAt IS NULL")
    void markAsRead(@Param("applicationId") Long applicationId, @Param("receiverId") Long receiverId);
}
