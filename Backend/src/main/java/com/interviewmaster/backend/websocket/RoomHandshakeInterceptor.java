package com.interviewmaster.backend.websocket;

import cn.dev33.satoken.stp.StpUtil;
import com.interviewmaster.backend.mapper.RoomAccessMapper;
import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_ROOM_CODE = "roomCode";
    public static final String ATTR_USER_ID = "userId";

    private static final Pattern ROOM_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9]{6}$");

    private final RoomAccessMapper roomAccessMapper;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        URI uri = request.getURI();
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        String roomCode = params.getFirst("roomCode");
        String token = normalizeToken(params.getFirst("token"));

        if (roomCode == null || !ROOM_CODE_PATTERN.matcher(roomCode).matches()) {
            log.warn("room websocket rejected: invalid roomCode");
            return reject(response, HttpStatus.BAD_REQUEST);
        }
        if (token == null) {
            log.warn("room websocket rejected: missing token, roomCode={}", roomCode);
            return reject(response, HttpStatus.UNAUTHORIZED);
        }
        Long userId = resolveUserId(token);
        if (userId == null) {
            log.warn("room websocket rejected: invalid token, roomCode={}", roomCode);
            return reject(response, HttpStatus.UNAUTHORIZED);
        }
        if (roomAccessMapper.countActiveMembership(roomCode, userId) <= 0) {
            log.warn("room websocket rejected: not an active room member, roomCode={}, userId={}",
                    roomCode, userId);
            return reject(response, HttpStatus.FORBIDDEN);
        }

        attributes.put(ATTR_ROOM_CODE, roomCode);
        attributes.put(ATTR_USER_ID, userId);
        log.info("room websocket accepted, roomCode={}, userId={}", roomCode, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private Long resolveUserId(String token) {
        try {
            Object loginId = StpUtil.getLoginIdByToken(token);
            return loginId == null ? null : Long.valueOf(loginId.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String normalized = token.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            normalized = normalized.substring(7).trim();
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean reject(ServerHttpResponse response, HttpStatus status) {
        response.setStatusCode(status);
        return false;
    }
}
