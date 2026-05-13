package com.project.blackjack.domain.game.controller;

import com.project.blackjack.domain.game.dto.*;
import com.project.blackjack.domain.game.entity.BlackjackGame;
import com.project.blackjack.domain.game.service.GameService;
import com.project.blackjack.domain.room.entity.Room;
import com.project.blackjack.domain.room.repository.RoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameController {
    private final GameService gameService;
    private final RoomManager roomManager;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/game/seat")
    public void takeSeat(SeatRequest request) {
        log.info("자리 착석 요청 - 방: {}, 유저: {}, 자리: {}", request.getRoomId(), request.getUsername(), request.getSeatIndex());
        try {
            gameService.sitDown(request.getRoomId(), request.getUsername(), request.getSeatIndex());
        } catch (Exception e) {
            sendError(request.getRoomId(), request.getUsername(), e.getMessage());
        }
    }

    @MessageMapping("/game/start")
    public void startGame(GameStartRequest request) {
        log.info("게임 시작 요청 - 방: {}, 요청자: {}", request.getRoomId(), request.getUsername());
        try {
            gameService.setupGame(request.getRoomId());
        } catch (Exception e) {
            sendError(request.getRoomId(), request.getUsername(), e.getMessage());
        }
    }

    @MessageMapping("/game/bet")
    public void placeBet(BetRequest request) {
        log.info("배팅 접수 - 방: {}, 유저: {}, 금액: {}", request.getRoomId(), request.getUsername(), request.getBetAmount());
        try {
            gameService.placeBet(request.getRoomId(), request.getUsername(), request.getBetAmount());
        } catch (Exception e) {
            sendError(request.getRoomId(), request.getUsername(), e.getMessage());
        }
    }

    @MessageMapping("/game/insurance")
    public void handleInsurance(InsuranceRequest request) {
        log.info("보험 선택 요청 - 방: {}, 유저: {}, 수락: {}", request.getRoomId(), request.getUsername(), request.isAccept());
        gameService.handleInsuranceChoice(request.getRoomId(), request.getUsername(), request.isAccept());
        broadcastGameState(request.getRoomId());
    }

    @MessageMapping("/game/action")
    public void handleAction(PlayerActionRequest request) {
        log.info("플레이어 액션 요청 - 방: {}, 유저: {}, 액션: {}", request.getRoomId(), request.getUsername(), request.getAction());
        try {
            gameService.handlePlayerAction(
                    request.getRoomId(),
                    request.getUsername(),
                    request.getAction(),
                    request.getHandIndex()
            );
            broadcastGameState(request.getRoomId());
        } catch (Exception e) {
            sendError(request.getRoomId(), request.getUsername(), e.getMessage());
        }
    }

    private void broadcastGameState(String roomId) {
        Room room = roomManager.findById(roomId);
        if(room != null && room.getGame() != null) {
            BlackjackGame game = room.getGame();
            GameStatusResponse response = new GameStatusResponse(roomId, game, room.getSeats());
            messagingTemplate.convertAndSend("/topic/room/" + roomId, response);
        }
    }

    private void sendError(String roomId, String username, String message) {
        GameErrorResponse errorResponse = new GameErrorResponse(username, message);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/errors", errorResponse);
    }

    @MessageMapping("/game/sync")
    public void syncGame(Map<String, String> payload) {
        String roomId = payload.get("roomId");
        if (roomId != null) {
            gameService.syncGameState(roomId);
        }
    }
}