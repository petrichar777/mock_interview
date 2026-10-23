package com.interviewmaster.backend.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomEventPublisher {

    private final RoomWebSocketSessionRegistry sessionRegistry;

    public void publish(String roomCode, RoomEventType type, Object payload) {
        if (roomCode == null || roomCode.isBlank()) {
            return;
        }
        sessionRegistry.broadcast(roomCode, RoomEvent.of(type, roomCode, payload));
    }
}
