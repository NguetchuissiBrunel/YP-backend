package com.yowpainter.modules.chat.domain.port.out;

import com.yowpainter.modules.chat.domain.model.ChatMessage;
import com.yowpainter.modules.chat.domain.model.ChatMessageStatus;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepositoryPort {

    ChatMessage save(ChatMessage message);

    List<ChatMessage> findByChatIdOrderByTimestampAsc(String chatId);

    long countByRecipientIdAndSenderIdAndStatus(UUID recipientId, UUID senderId, ChatMessageStatus status);

    void markAsRead(UUID recipientId, UUID senderId);
}
