package com.project.blackjack.domain.game.controller;
import com.project.blackjack.domain.game.dto.*;
import com.project.blackjack.domain.game.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import java.security.Principal;
import java.util.Map;
@Controller
@RequiredArgsConstructor
public class GameController {
    private final GameService service;
    private String user(String room, Principal principal) {
        if (principal == null) throw new IllegalArgumentException("로그인이 필요합니다.");
        service.requireMember(room, principal.getName()); return principal.getName();
    }
    @MessageMapping("/game/seat") public void seat(SeatRequest r, Principal p) { service.sitDown(r.getRoomId(), user(r.getRoomId(), p), r.getSeatIndex()); }
    @MessageMapping("/game/start") public void start(GameStartRequest r, Principal p) { service.setupGame(r.getRoomId(), user(r.getRoomId(), p)); }
    @MessageMapping("/game/resume") public void resume(GameStartRequest r, Principal p) { service.resume(r.getRoomId(), user(r.getRoomId(), p)); }
    @MessageMapping("/game/bet") public void bet(BetRequest r, Principal p) { service.placeBet(r.getRoomId(), user(r.getRoomId(), p), r.getBetAmount()); }
    @MessageMapping("/game/insurance") public void insurance(InsuranceRequest r, Principal p) { service.handleInsuranceChoice(r.getRoomId(), user(r.getRoomId(), p), r.isAccept()); }
    @MessageMapping("/game/action") public void action(PlayerActionRequest r, Principal p) { service.handlePlayerAction(r.getRoomId(), user(r.getRoomId(), p), r.getAction(), r.getHandIndex(), r.getRevision()); }
    @MessageMapping("/game/sync") public void sync(Map<String,String> r, Principal p) { user(r.get("roomId"), p); service.syncGameState(r.get("roomId")); }
    @MessageExceptionHandler(Exception.class)
    @SendToUser(value="/queue/errors", broadcast=false)
    public Map<String,String> error(Exception e) {
        return Map.of("message", e instanceof IllegalArgumentException ? e.getMessage() : "요청을 처리하지 못했습니다. 상태를 확인 후 다시 시도해주세요.");
    }
}
