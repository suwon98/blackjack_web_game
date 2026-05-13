package com.project.blackjack.global.config;

import com.project.blackjack.domain.room.service.RoomService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final RoomService roomService;
    private final Map<String, SessionInfo> sessionMap = new ConcurrentHashMap<>();

    @Getter
    @AllArgsConstructor
    private static class SessionInfo {
        private String roomId;
        private String username;
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = accessor.getSessionId();
        String username = accessor.getFirstNativeHeader("username");
        String roomId = accessor.getFirstNativeHeader("roomId");

        if(username != null && roomId != null) {
            sessionMap.put(sessionId, new SessionInfo(roomId, username));
            log.info("웹소켓 연결됨 - SessionId: {}, Username: {}, RoomId: {}", sessionId, username, roomId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        SessionInfo info = sessionMap.remove(sessionId);

        if(info != null) {
            log.info("웹소켓 끊어짐 - SessionId: {}, Username: {}, RoomId: {}", sessionId, info.getUsername(), info.getRoomId());

            try {
                roomService.leaveRoom(info.getRoomId(), info.getUsername());
                log.info("유저 [{}] 방 [{}]에서 자동 퇴장 처리 완료", info.getUsername(), info.getRoomId());
            } catch (Exception e) {
                log.warn("자동 퇴장 무시됨 (이미 나갔거나 방이 없음): {}", e.getMessage());
            }
        }
    }
}
