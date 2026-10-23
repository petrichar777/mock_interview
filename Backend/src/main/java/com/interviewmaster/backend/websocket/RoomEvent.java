package com.interviewmaster.backend.websocket;

import java.time.Instant;

public record RoomEvent(String type, String roomCode, Object payload, Instant timestamp) {

    public static RoomEvent of(RoomEventType type, String roomCode, Object payload) {
        return new RoomEvent(type.name(), roomCode, payload, Instant.now());
    }
}
