package com.project.blackjack.domain.member.controller;

import com.project.blackjack.domain.auth.dto.LoginRequest;
import com.project.blackjack.domain.auth.dto.TokenResponse;
import com.project.blackjack.domain.member.dto.MemberInfoResponse;
import com.project.blackjack.domain.member.dto.MemberJoinRequest;
import com.project.blackjack.domain.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;
    private final com.project.blackjack.domain.game.repository.ReceiptRepository receipts;
    @GetMapping("/history") public Object history(Authentication auth) { return receipts.findTop20ByUsernameOrderByCreatedAtDesc(auth.getName()); }

    @PostMapping("/join")
    public ResponseEntity<String> join(@Valid @RequestBody MemberJoinRequest request) {
        Long memberId = memberService.join(request);
        return ResponseEntity.ok(memberId + "번 회원님, 가입이 완료되었습니다.");
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse tokenResponse = memberService.login(request);
        return ResponseEntity.ok(tokenResponse);
    }

    @GetMapping("/me")
    public ResponseEntity<MemberInfoResponse> getMyInfo(Authentication authentication) {
        String username = authentication.getName();

        MemberInfoResponse response = memberService.getMyInfo(username);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/recharge")
    public ResponseEntity<Long> recharge(Authentication authentication) {
        String username = authentication.getName();
        Long updatedMoney = memberService.rechargeMoney(username);
        return ResponseEntity.ok(updatedMoney);
    }

    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse> reissue(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        TokenResponse tokenResponse = memberService.reissue(refreshToken);
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(Authentication authentication) {
        String username = authentication.getName();
        memberService.logout(username);
        return ResponseEntity.ok("성공적으로 로그아웃 되었습니다.");
    }
}