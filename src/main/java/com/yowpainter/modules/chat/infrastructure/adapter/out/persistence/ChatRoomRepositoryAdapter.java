package com.yowpainter.modules.chat.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.chat.domain.model.ChatRoom;
import com.yowpainter.modules.chat.domain.port.out.ChatRoomRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatRoomRepositoryAdapter implements ChatRoomRepositoryPort {

    private final ChatRoomJpaRepository jpaRepository;

    @Override
    public ChatRoom save(ChatRoom room) {
        return jpaRepository.save(room);
    }

    @Override
    public java.util.Optional<ChatRoom> findBySenderIdAndRecipientId(UUID senderId, UUID recipientId) {
        return jpaRepository.findBySender_IdAndRecipient_Id(senderId, recipientId);
    }

    @Override
    public java.util.List<ChatRoom> findBySenderIdOrRecipientId(UUID senderId, UUID recipientId) {
        return jpaRepository.findBySender_IdOrRecipient_Id(senderId, recipientId);
    }
}
