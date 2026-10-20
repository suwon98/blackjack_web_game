package com.project.blackjack;

import com.project.blackjack.domain.room.entity.Room;
import com.project.blackjack.domain.room.repository.RoomManager;
import com.project.blackjack.global.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.support.MessageBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboundGuardTests {
    @Test void simpleBrokerMessagesAreCheckedAfterLeaveAndLogout() {
        var rooms = mock(RoomManager.class);
        var sockets = new SocketSessions();
        var tokens = mock(JwtTokenProvider.class);
        var sessions = mock(RefreshTokenStore.class);
        var guard = new OutboundRoomGuard(rooms, sockets, tokens, sessions);
        var room = Room.create("room", 2, false, null, 1000);
        room.joinRoom("alice");
        when(rooms.findById(room.getRoomId())).thenReturn(room);
        sockets.put("socket", "alice", "token");
        when(tokens.validateAccessToken("token")).thenReturn(true);
        when(tokens.sessionId("token")).thenReturn("session");
        when(sessions.active("alice", "session")).thenReturn(true);
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId("socket");
        headers.setDestination("/topic/chat/room/" + room.getRoomId());
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertNotNull(guard.preSend(message, null));
        room.leaveRoom("alice");
        assertNull(guard.preSend(message, null));
        var lobbyHeaders = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        lobbyHeaders.setSessionId("socket");
        lobbyHeaders.setDestination("/topic/chat/lobby");
        var lobby = MessageBuilder.createMessage(new byte[0], lobbyHeaders.getMessageHeaders());
        assertNotNull(guard.preSend(lobby, null));
        when(sessions.active("alice", "session")).thenReturn(false);
        assertNull(guard.preSend(lobby, null));
    }
}
