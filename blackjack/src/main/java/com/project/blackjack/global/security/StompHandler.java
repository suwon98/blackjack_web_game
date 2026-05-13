package com.project.blackjack.global.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if(accessor != null && StompCommand.CONNECTED.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if(authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if(jwtTokenProvider.validateToken(token)) {
                    Authentication auth = jwtTokenProvider.getAuthentication(token);
                    accessor.setUser(auth);
                    log.info("웹소켓 인증 성공 - User: {}", auth.getName());
                } else {
                    log.error("웹소켓 인증 실패: 유효하지 않은 토큰");
                    throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
                }
            } else {
                log.error("웹소켓 인증 실패: 헤더에 토큰이 없음");
                throw new IllegalArgumentException("인증 헤더가 없습니다.");
            }
        }
        return message;
    }
}
