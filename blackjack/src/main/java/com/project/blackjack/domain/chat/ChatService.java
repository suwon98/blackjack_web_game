package com.project.blackjack.domain.chat;

import com.project.blackjack.domain.member.repository.MemberRepository;
import com.project.blackjack.domain.room.repository.RoomManager;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatService {
    public record Request(String roomId, String kind, String text) {}
    public record Message(String id, String username, String nickname, String kind, String text, long sentAt) {}
    private final RoomManager rooms;
    private final MemberRepository members;
    private final SimpMessagingTemplate messaging;
    private final Map<String, ArrayDeque<Long>> recent = new LinkedHashMap<>();
    private static final Set<String> EMOJI = Set.of("👋", "👍", "👏", "🎉", "😮", "🍀");

    public void send(String username, Request request) {
        if (username == null) throw new IllegalArgumentException("로그인이 필요합니다.");
        if (request == null || request.text() == null || !Set.of("TEXT", "EMOJI").contains(request.kind() == null ? "" : request.kind()))
            throw new IllegalArgumentException("메시지 형식을 확인해주세요.");
        String text = request.text().strip();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > 300 || text.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("메시지는 제어 문자 없이 1~300자로 입력해주세요.");
        if ("EMOJI".equals(request.kind()) && !EMOJI.contains(text))
            throw new IllegalArgumentException("지원하지 않는 이모지입니다.");
        var member = members.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        limit(username);
        Message message = new Message(UUID.randomUUID().toString(), username, member.getNickname(), request.kind(), text, System.currentTimeMillis());
        if (request.roomId() == null) {
            messaging.convertAndSend("/topic/chat/lobby", message);
        } else {
            var room = rooms.findById(request.roomId());
            if (room == null) throw new IllegalArgumentException("방을 찾을 수 없습니다.");
            synchronized (room) {
                if (!room.contains(username)) throw new IllegalArgumentException("방에 먼저 입장해주세요.");
                messaging.convertAndSend("/topic/chat/room/" + room.getRoomId(), message);
            }
        }
    }

    private synchronized void limit(String username) {
        long now = System.currentTimeMillis();
        recent.entrySet().removeIf(entry -> entry.getValue().isEmpty() || entry.getValue().peekLast() <= now - 10000);
        if (!recent.containsKey(username) && recent.size() >= 4096)
            throw new IllegalArgumentException("채팅이 혼잡합니다. 잠시 후 다시 시도해주세요.");
        var times = recent.computeIfAbsent(username, key -> new ArrayDeque<>());
        while (!times.isEmpty() && times.peekFirst() <= now - 10000) times.removeFirst();
        if (times.size() >= 5) throw new IllegalArgumentException("채팅은 10초에 5번까지 보낼 수 있습니다.");
        times.addLast(now);
    }
}
