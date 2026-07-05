package com.yowpainter.modules.chat.domain.port.out;

import com.yowpainter.modules.chat.domain.model.ChatRoom;
import java.util.UUID;

public interface ChatRoomRepositoryPort {

    ChatRoom save(ChatRoom room);
    java.util.Optional<ChatRoom> findBySenderIdAndRecipientId(UUID senderId, UUID recipientId);
    java.util.List<ChatRoom> findBySenderIdOrRecipientId(UUID senderId, UUID recipientId);
}
