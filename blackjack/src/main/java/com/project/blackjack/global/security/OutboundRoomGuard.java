package com.project.blackjack.global.security;
import com.project.blackjack.domain.room.repository.RoomManager;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class OutboundRoomGuard implements ChannelInterceptor {
    private final RoomManager rooms;
    private final SocketSessions sockets;
    private final JwtTokenProvider tokens;
    private final RefreshTokenStore sessions;
    @Override public Message<?> preSend(Message<?> message,MessageChannel channel) {
        var a=StompHeaderAccessor.wrap(message);String dest=a.getDestination();
        if(a.getMessageType()==org.springframework.messaging.simp.SimpMessageType.MESSAGE && dest!=null && (dest.startsWith("/topic/room/") || dest.startsWith("/topic/chat/"))) {
            var session=sockets.get(a.getSessionId());
            if(session==null || !tokens.validateAccessToken(session.token()) || !sessions.active(session.username(),tokens.sessionId(session.token()))) return null;
            if (!"/topic/chat/lobby".equals(dest)) {
                String prefix = dest.startsWith("/topic/chat/room/") ? "/topic/chat/room/" : "/topic/room/";
                var room=rooms.findById(dest.substring(prefix.length()));
                if(room==null || !room.contains(session.username())) return null;
            }
        }
        return message;
    }
}
