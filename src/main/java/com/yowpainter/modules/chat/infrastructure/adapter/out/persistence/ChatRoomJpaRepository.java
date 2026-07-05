package com.yowpainter.modules.chat.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.chat.domain.model.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ChatRoomJpaRepository extends JpaRepository<ChatRoom, java.util.UUID> {

    java.util.Optional<ChatRoom> findBySender_IdAndRecipient_Id(UUID senderId, UUID recipientId);
    java.util.List<ChatRoom> findBySender_IdOrRecipient_Id(UUID senderId, UUID recipientId);
}
