package com.interviewmaster.backend.websocket;

import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 只负责房间进度推送，不接收聊天消息、不做回放与断线补齐。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private final RoomWebSocketSessionRegistry sessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String roomCode = (String) session.getAttributes().get(RoomHandshakeInterceptor.ATTR_ROOM_CODE);
        Long userId = (Long) session.getAttributes().get(RoomHandshakeInterceptor.ATTR_USER_ID);
        WebSocketSession decorated = sessionRegistry.register(roomCode, session);
        sessionRegistry.send(decorated, RoomEvent.of(
                RoomEventType.CONNECTED, roomCode, Map.of("userId", userId)));
        log.info("room websocket connected, roomCode={}, userId={}, sessionId={}",
                roomCode, userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.debug("ignored inbound room websocket message, sessionId={}, length={}",
                session.getId(), message.getPayloadLength());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        unregister(session);
        log.info("room websocket closed, sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("room websocket transport error, sessionId={}, reason={}",
                session.getId(), exception.getMessage());
        unregister(session);
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ex) {
            log.debug("failed to close broken websocket session, sessionId={}", session.getId(), ex);
        }
    }

    private void unregister(WebSocketSession session) {
        String roomCode = (String) session.getAttributes().get(RoomHandshakeInterceptor.ATTR_ROOM_CODE);
        if (roomCode != null) {
            sessionRegistry.unregister(roomCode, session.getId());
        }
    }
}
