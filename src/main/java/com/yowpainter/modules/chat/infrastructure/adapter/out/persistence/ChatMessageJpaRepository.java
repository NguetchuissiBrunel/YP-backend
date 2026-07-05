package com.yowpainter.modules.chat.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.chat.domain.model.ChatMessage;
import com.yowpainter.modules.chat.domain.model.ChatMessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByChatIdOrderByTimestampAsc(String chatId);

    long countByRecipient_IdAndSender_IdAndStatus(UUID recipientId, UUID senderId, ChatMessageStatus status);

    @Modifying
    @Query("UPDATE ChatMessage m SET m.status = com.yowpainter.modules.chat.domain.model.ChatMessageStatus.READ WHERE m.recipient.id = :recipientId AND m.sender.id = :senderId AND m.status = com.yowpainter.modules.chat.domain.model.ChatMessageStatus.SENT")
    void markAsRead(@Param("recipientId") UUID recipientId, @Param("senderId") UUID senderId);
}
