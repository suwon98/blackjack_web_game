package com.project.blackjack.global.config;
import com.project.blackjack.domain.room.service.RoomService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;
import java.util.concurrent.*;
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {
    private final RoomService rooms;
    private volatile boolean stopping;
    @EventListener(org.springframework.context.event.ContextClosedEvent.class) public void stopping() { stopping=true; }
    private record Session(String room, String user) {}
    private final ConcurrentHashMap<String,Session> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "disconnect-grace"); t.setDaemon(true); return t; });
    @PreDestroy public void close() { timer.shutdownNow(); }
    @EventListener public void connect(SessionConnectEvent e) {
        var a = StompHeaderAccessor.wrap(e.getMessage());
        if (a.getUser() != null && a.getFirstNativeHeader("roomId") != null)
            sessions.put(a.getSessionId(), new Session(a.getFirstNativeHeader("roomId"), a.getUser().getName()));
    }
    @EventListener public void disconnect(SessionDisconnectEvent e) {
        if(stopping) return;
        Session s = sessions.remove(e.getSessionId()); if (s == null) return;
        timer.schedule(() -> { if (!stopping && !sessions.containsValue(s)) rooms.leaveRoom(s.room(), s.user()); }, 15, TimeUnit.SECONDS);
    }
}
