package com.project.blackjack.domain.room.service;
import com.project.blackjack.domain.room.dto.*;
import com.project.blackjack.domain.room.entity.Room;
import com.project.blackjack.domain.room.repository.RoomManager;
import com.project.blackjack.domain.game.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
@RequiredArgsConstructor
public class RoomService {
    private final RoomManager roomManager;
    private final GameService gameService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwords;
    private void ensureNoOtherRoom(String username, String id) {
        if (roomManager.findAll().stream().anyMatch(r -> !r.getRoomId().equals(id) && r.contains(username)))
            throw new IllegalArgumentException("기존 방에서 먼저 나가주세요.");
    }
    public synchronized RoomResponse createRoom(RoomCreateRequest request, String username) {
        ensureNoOtherRoom(username, "");
        if (request.getTitle() == null || request.getTitle().isBlank() || request.getTitle().length() > 60)
            throw new IllegalArgumentException("방 제목은 1~60자여야 합니다.");
        if (request.getMaxPlayers() < 1 || request.getMaxPlayers() > 6) throw new IllegalArgumentException("정원은 1~6명입니다.");
        if (request.getMinBet() < 100 || request.getMinBet() > 1000000 || request.getMinBet() % 100 != 0)
            throw new IllegalArgumentException("최소 베팅은 100~1,000,000 사이의 100 단위입니다.");
        if (request.isPrivate() && (request.getInviteCode() == null || request.getInviteCode().isBlank() || request.getInviteCode().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72))
            throw new IllegalArgumentException("비공개 방의 초대 코드를 입력하세요.");
        Room room = Room.create(request.getTitle().trim(), request.getMaxPlayers(), request.isPrivate(), request.isPrivate()?passwords.encode(request.getInviteCode()):null, request.getMinBet());
        room.joinRoom(username); roomManager.save(room);
        return new RoomResponse(room);
    }
    public List<RoomResponse> getRoomList(String username) {
        return roomManager.findAll().stream().map(r -> { synchronized(r) { RoomResponse response=new RoomResponse(r);response.setJoined(r.contains(username));return response; } }).toList();
    }
    public synchronized RoomResponse joinRoom(String id, String username, String inviteCode) {
        Room room = roomManager.findById(id);
        if (room == null) throw new IllegalArgumentException("존재하지 않는 방입니다.");
        ensureNoOtherRoom(username, id);
        synchronized(room) {
            if (room.contains(username)) return new RoomResponse(room);
            if (room.getTotalPlayerCount() >= room.getMaxPlayers()) throw new IllegalArgumentException("방 인원이 꽉 찼습니다.");
            if (room.isPrivate() && (inviteCode==null || !passwords.matches(inviteCode,room.getInviteCode()))) throw new IllegalArgumentException("초대 코드가 일치하지 않습니다.");
            gameService.joinObserver(id,username);
            return new RoomResponse(room);
        }
    }
    public synchronized void leaveRoom(String id, String username) {
        Room room = roomManager.findById(id);
        if (room == null) return;
        synchronized(room) {
            if (!room.contains(username)) return;
            gameService.handlePlayerLeave(id, username);
            if (room.getTotalPlayerCount() == 0) roomManager.delete(id);
        }
    }
}
