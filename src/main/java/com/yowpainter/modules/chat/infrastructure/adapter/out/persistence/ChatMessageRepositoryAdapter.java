package com.yowpainter.modules.chat.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.chat.domain.model.ChatMessage;
import com.yowpainter.modules.chat.domain.model.ChatMessageStatus;
import com.yowpainter.modules.chat.domain.port.out.ChatMessageRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatMessageRepositoryAdapter implements ChatMessageRepositoryPort {

    private final ChatMessageJpaRepository jpaRepository;

    @Override
    public ChatMessage save(ChatMessage message) {
        return jpaRepository.save(message);
    }

    @Override
    public List<ChatMessage> findByChatIdOrderByTimestampAsc(String chatId) {
        return jpaRepository.findByChatIdOrderByTimestampAsc(chatId);
    }

    @Override
    public long countByRecipientIdAndSenderIdAndStatus(UUID recipientId, UUID senderId, ChatMessageStatus status) {
        return jpaRepository.countByRecipient_IdAndSender_IdAndStatus(recipientId, senderId, status);
    }

    @Override
    @Transactional
    public void markAsRead(UUID recipientId, UUID senderId) {
        jpaRepository.markAsRead(recipientId, senderId);
    }
}
