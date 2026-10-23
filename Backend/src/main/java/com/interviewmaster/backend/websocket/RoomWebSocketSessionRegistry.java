package com.interviewmaster.backend.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomWebSocketSessionRegistry {

    private static final int SEND_TIME_LIMIT_MILLIS = 5_000;
    private static final int SEND_BUFFER_SIZE_BYTES = 512 * 1024;

    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    public WebSocketSession register(String roomCode, WebSocketSession session) {
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MILLIS, SEND_BUFFER_SIZE_BYTES);
        roomSessions.computeIfAbsent(roomCode, key -> new ConcurrentHashMap<>())
                .put(session.getId(), decorated);
        return decorated;
    }

    public void unregister(String roomCode, String sessionId) {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomCode);
        if (sessions == null) {
            return;
        }
        sessions.remove(sessionId);
        if (sessions.isEmpty()) {
            roomSessions.remove(roomCode, sessions);
        }
    }

    public void send(WebSocketSession session, RoomEvent event) {
        String message;
        try {
            message = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            log.error("failed to serialize room event, type={}, roomCode={}", event.type(), event.roomCode(), ex);
            return;
        }
        sendMessage(session, message);
    }

    public void broadcast(String roomCode, RoomEvent event) {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomCode);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String message;
        try {
            message = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            log.error("failed to serialize room event, type={}, roomCode={}", event.type(), roomCode, ex);
            return;
        }
        List<String> failedSessionIds = new ArrayList<>();
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            if (!sendMessage(entry.getValue(), message)) {
                failedSessionIds.add(entry.getKey());
            }
        }
        failedSessionIds.forEach(sessionId -> unregister(roomCode, sessionId));
    }

    private boolean sendMessage(WebSocketSession session, String payload) {
        if (!session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(payload));
            return true;
        } catch (IOException | RuntimeException ex) {
            log.debug("failed to push room event, sessionId={}, reason={}", session.getId(), ex.getMessage());
            return false;
        }
    }
}
