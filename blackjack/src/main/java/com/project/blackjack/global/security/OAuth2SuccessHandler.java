package com.project.blackjack.global.security;

import com.project.blackjack.domain.member.entity.Member;
import com.project.blackjack.domain.member.repository.MemberRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    @org.springframework.beans.factory.annotation.Value("${app.frontend-url}")
    private String frontendUrl;
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;
    private final com.project.blackjack.global.security.RefreshTokenStore tokenStore;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        Member member = memberRepository.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String refreshToken = jwtTokenProvider.createRefreshToken(member.getUsername());
        String accessToken = jwtTokenProvider.createAccessToken(member.getUsername(), member.getRole(),jwtTokenProvider.tokenId(refreshToken));

        tokenStore.save(member.getUsername(), refreshToken);

        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth2/callback")
                .fragment("accessToken=" + accessToken + "&refreshToken=" + refreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
