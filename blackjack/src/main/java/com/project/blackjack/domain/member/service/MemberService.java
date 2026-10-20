package com.project.blackjack.domain.member.service;

import com.project.blackjack.domain.auth.dto.LoginRequest;
import com.project.blackjack.domain.auth.dto.TokenResponse;
import com.project.blackjack.domain.member.dto.MemberInfoResponse;
import com.project.blackjack.domain.member.dto.MemberJoinRequest;
import com.project.blackjack.domain.member.entity.Member;
import com.project.blackjack.domain.member.entity.Role;
import com.project.blackjack.domain.member.repository.MemberRepository;
import com.project.blackjack.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final com.project.blackjack.global.security.RefreshTokenStore tokenStore;

    @Transactional
    public Long join(MemberJoinRequest request) {
        if (memberRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (memberRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        String encryptedPassword = passwordEncoder.encode(request.getPassword());

        Member member = Member.builder()
                .username(request.getUsername())
                .password(encryptedPassword)
                .nickname(request.getNickname())
                .role(Role.USER)
                .money(100000L)
                .build();

        return memberRepository.save(member).getId();
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 아이디입니다."));

        if(!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        String refreshToken = jwtTokenProvider.createRefreshToken(member.getUsername());
        String accessToken = jwtTokenProvider.createAccessToken(member.getUsername(), member.getRole(),jwtTokenProvider.tokenId(refreshToken));

        tokenStore.save(member.getUsername(), refreshToken);

        return new TokenResponse(accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public MemberInfoResponse getMyInfo(String username) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return new MemberInfoResponse(member.getUsername(), member.getNickname(), member.getRole(), member.getMoney());
    }

    @Transactional
    public Long rechargeMoney(String username) {
        Member member = memberRepository.lockByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (member.getMoney() >= 1000) throw new IllegalArgumentException("잔액이 1,000 미만일 때만 칩 충전할 수 있습니다.");
        member.addMoney(100000L);
        return member.getMoney();
    }

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Refresh Token이 유효하지 않거나 만료되었습니다.");
        }

        String username = jwtTokenProvider.getAuthentication(refreshToken).getName();

        if (!tokenStore.matches(username, refreshToken)) {
            throw new IllegalArgumentException("로그아웃 된 사용자이거나, 일치하지 않는 토큰입니다.");
        }

        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String newRefreshToken = jwtTokenProvider.createRefreshToken(member.getUsername());
        String newAccessToken = jwtTokenProvider.createAccessToken(member.getUsername(), member.getRole(),jwtTokenProvider.tokenId(newRefreshToken));

        tokenStore.save(member.getUsername(), newRefreshToken);

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(String username) {
        tokenStore.delete(username);
    }
}
