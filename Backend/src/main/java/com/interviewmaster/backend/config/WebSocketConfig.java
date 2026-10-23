package com.interviewmaster.backend.config;

import com.interviewmaster.backend.websocket.RoomHandshakeInterceptor;
import com.interviewmaster.backend.websocket.RoomWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    // 与 CorsConfig 的允许来源保持一致
    private static final String[] ALLOWED_ORIGIN_PATTERNS = {
            "http://localhost:*",
            "http://106.53.9.56:*",
            "https://www.augensternchat.cn",
            "https://api.augensternchat.cn"
    };

    private final RoomWebSocketHandler roomWebSocketHandler;
    private final RoomHandshakeInterceptor roomHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(roomWebSocketHandler, "/ws/rooms")
                .addInterceptors(roomHandshakeInterceptor)
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
    }
}
