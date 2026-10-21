package com.project.blackjack.global.security;
import com.project.blackjack.domain.room.repository.RoomManager;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {
    private final JwtTokenProvider tokens;
    private final RefreshTokenStore sessions;
    private final RoomManager rooms;
    private final SocketSessions sockets;
    @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor a = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (a == null) return message;
        if (a.getCommand() == StompCommand.CONNECT) {
            String header = a.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ") || !tokens.validateAccessToken(header.substring(7))) throw new IllegalArgumentException("로그인이 필요합니다.");
            var auth = tokens.getAuthentication(header.substring(7));
            if (!"lobby".equals(a.getFirstNativeHeader("channel"))) {
                var room = rooms.findById(a.getFirstNativeHeader("roomId"));
                if (room == null || !room.contains(auth.getName())) throw new IllegalArgumentException("방에 먼저 입장해주세요.");
            }
            if(!sessions.active(auth.getName(),tokens.sessionId(header.substring(7)))) throw new IllegalArgumentException("만료된 세션입니다.");
            a.getSessionAttributes().put("accessToken",header.substring(7));
            a.setUser(auth);
            sockets.put(a.getSessionId(),auth.getName(),header.substring(7));
        } else if (a.getCommand() == StompCommand.SEND || a.getCommand() == StompCommand.SUBSCRIBE) {
            long bucket=System.currentTimeMillis()/10000;
            var attributes=a.getSessionAttributes();
            synchronized(attributes) {
                if(!Long.valueOf(bucket).equals(attributes.get("bucket"))) { attributes.put("bucket",bucket);attributes.put("count",0); }
                int count=(int)attributes.getOrDefault("count",0)+1;attributes.put("count",count);
                if(count>60) throw new IllegalArgumentException("요청이 너무 빠릅니다.");
            }
            if (a.getUser() == null) throw new IllegalArgumentException("로그인이 필요합니다.");
            String token=(String)a.getSessionAttributes().get("accessToken");
            if(!tokens.validateAccessToken(token) || !sessions.active(a.getUser().getName(),tokens.sessionId(token))) throw new IllegalArgumentException("세션이 만료되었습니다. 다시 연결해주세요.");
            String dest = a.getDestination();
            if (a.getCommand() == StompCommand.SEND) {
                if (!"/app/chat/send".equals(dest) && (dest == null || !dest.matches("/app/game/(seat|start|ready|resume|bet|insurance|action|sync)"))) throw new IllegalArgumentException("허용하지 않는 경로입니다.");
            } else if (!"/user/queue/errors".equals(dest) && !"/user/queue/room-events".equals(dest)) {
                if ("/topic/chat/lobby".equals(dest)) return message;
                String prefix = dest != null && dest.startsWith("/topic/chat/room/") ? "/topic/chat/room/" : "/topic/room/";
                if (dest == null || !dest.startsWith(prefix)) throw new IllegalArgumentException("허용하지 않는 구독입니다.");
                var room = rooms.findById(dest.substring(prefix.length()));
                if (room == null || !room.contains(a.getUser().getName())) throw new IllegalArgumentException("방에 먼저 입장해주세요.");
            }
        }
        if(a.getCommand()==StompCommand.DISCONNECT) sockets.remove(a.getSessionId());
        return message;
    }
}
