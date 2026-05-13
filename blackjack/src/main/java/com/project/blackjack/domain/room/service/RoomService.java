package com.project.blackjack.domain.room.service;

import com.project.blackjack.domain.game.entity.GameState;
import com.project.blackjack.domain.room.dto.RoomCreateRequest;
import com.project.blackjack.domain.room.dto.RoomResponse;
import com.project.blackjack.domain.room.entity.Room;
import com.project.blackjack.domain.room.repository.RoomManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final RoomManager roomManager;

    public RoomResponse createRoom(RoomCreateRequest request, String username) {
        Room room = Room.create(
                request.getTitle(),
                request.getMaxPlayers(),
                request.isPrivate(),
                request.getInviteCode(),
                request.getMinBet()
        );
        room.joinRoom(username);
        roomManager.save(room);
        return new RoomResponse(room);
    }

    public List<RoomResponse> getRoomList() {
        return roomManager.findAll().stream()
                .map(RoomResponse::new)
                .collect(Collectors.toList());
    }

    public RoomResponse joinRoom(String roomId, String username, String inviteCode) {
        Room room = roomManager.findById(roomId);
        if(room == null) throw new IllegalArgumentException("존재하지 않는 방입니다.");

        if(room.getTotalPlayerCount() >= room.getMaxPlayers()) {
            throw new IllegalArgumentException("방 인원이 꽉 찼습니다.");
        }

        if(room.isPrivate() && !room.getInviteCode().equals(inviteCode)) {
            throw new IllegalArgumentException("초대 코드가 일치하지 않습니다.");
        }

        if(room.getSeats().containsValue(username) || room.getObservers().contains(username)) {
            throw new IllegalArgumentException("이미 입장한 방입니다.");
        }

        room.joinRoom(username);
        return new RoomResponse(room);
    }

    public void leaveRoom(String roomId, String username) {
        Room room = roomManager.findById(roomId);
        if(room != null) {
            room.leaveRoom(username);
            if(room.getTotalPlayerCount() == 0) {
                roomManager.delete(roomId);
            }
        }
    }
}
