package com.project.blackjack;

import com.project.blackjack.domain.chat.ChatService;
import com.project.blackjack.domain.member.entity.Member;
import com.project.blackjack.domain.member.repository.MemberRepository;
import com.project.blackjack.domain.room.entity.Room;
import com.project.blackjack.domain.room.repository.RoomManager;
import org.junit.jupiter.api.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatServiceTests {
    RoomManager rooms;
    SimpMessagingTemplate messaging;
    ChatService chats;
    @BeforeEach void setup() {
        rooms = mock(RoomManager.class);
        messaging = mock(SimpMessagingTemplate.class);
        var members = mock(MemberRepository.class);
        when(members.findByUsername("alice")).thenReturn(Optional.of(Member.builder().username("alice").nickname("Alice").build()));
        chats = new ChatService(rooms, members, messaging);
    }
    @Test void usesServerIdentityAndValidatesMessages() {
        chats.send("alice", new ChatService.Request(null, "TEXT", " <script>alert(1)</script> "));
        verify(messaging).convertAndSend(eq("/topic/chat/lobby"), argThat((ChatService.Message message) ->
                message.username().equals("alice") && message.nickname().equals("Alice") && message.text().equals("<script>alert(1)</script>")));
        for (String text : new String[]{" ", "x".repeat(301), "hello\nworld", "\u0000"}) {
            assertThrows(IllegalArgumentException.class, () -> chats.send("alice", new ChatService.Request(null, "TEXT", text)));
        }
        assertThrows(IllegalArgumentException.class, () -> chats.send("alice", new ChatService.Request(null, "EMOJI", "not-emoji")));
    }
    @Test void roomRequiresCurrentMembership() {
        var room = Room.create("private", 2, true, "hash", 1000);
        when(rooms.findById(room.getRoomId())).thenReturn(room);
        var request = new ChatService.Request(room.getRoomId(), "EMOJI", "👋");
        assertThrows(IllegalArgumentException.class, () -> chats.send("alice", request));
        room.joinRoom("alice");
        chats.send("alice", request);
        verify(messaging).convertAndSend(eq("/topic/chat/room/" + room.getRoomId()), any(ChatService.Message.class));
        room.leaveRoom("alice");
        assertThrows(IllegalArgumentException.class, () -> chats.send("alice", request));
    }
    @Test void rateLimitAppliesAcrossLobbyAndRooms() {
        var room = Room.create("room", 2, false, null, 1000); room.joinRoom("alice");
        when(rooms.findById(room.getRoomId())).thenReturn(room);
        for (int i = 0; i < 5; i++) chats.send("alice", new ChatService.Request(i % 2 == 0 ? null : room.getRoomId(), "TEXT", "hello"));
        assertThrows(IllegalArgumentException.class, () -> chats.send("alice", new ChatService.Request(null, "TEXT", "extra")));
    }
}
