package com.project.blackjack.domain.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chats;
    @MessageMapping("/chat/send")
    public void send(ChatService.Request request, Principal principal) {
        chats.send(principal == null ? null : principal.getName(), request);
    }
    @MessageExceptionHandler(Exception.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public Map<String, String> error(Exception error) {
        return Map.of("source", "chat", "message", error instanceof IllegalArgumentException ? error.getMessage() : "메시지를 보내지 못했습니다.");
    }
}
