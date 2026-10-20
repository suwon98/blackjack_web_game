package com.project.blackjack.domain.room.controller;

import com.project.blackjack.domain.room.dto.RoomCreateRequest;
import com.project.blackjack.domain.room.dto.RoomResponse;
import com.project.blackjack.domain.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getRooms(Authentication authentication) {
        return ResponseEntity.ok(roomService.getRoomList(authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @RequestBody RoomCreateRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(roomService.createRoom(request, username));
    }

    @PostMapping("/{roomId}/enter")
    public ResponseEntity<RoomResponse> enterRoom(
            @PathVariable String roomId,
            @RequestBody(required = false) com.project.blackjack.domain.room.dto.RoomJoinRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(roomService.joinRoom(roomId, username, request == null ? null : request.getInviteCode()));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<String> leaveRoom(
            @PathVariable String roomId,
            Authentication authentication) {
        String username = authentication.getName();
        roomService.leaveRoom(roomId, username);
        return ResponseEntity.ok("방에서 퇴장했습니다.");
    }
}