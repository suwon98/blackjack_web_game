package com.project.blackjack.global.security;

import com.project.blackjack.domain.member.entity.Member;
import com.project.blackjack.domain.member.entity.Role;
import com.project.blackjack.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final MemberRepository memberRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        Map<String, Object> attributes = oAuth2User.getAttributes();

        String provider = userRequest.getClientRegistration().getRegistrationId();
        String providerId = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        Optional<Member> memberOptional = memberRepository.findByUsername(email);

        Member member;
        if(memberOptional.isPresent()) {
            member = memberOptional.get();
        } else {
            member = Member.builder()
                    .username(email)
                    .nickname(name + "_" + providerId.substring(0, 5))
                    .role(Role.USER)
                    .money(100000L)
                    .provider(provider)
                    .providerId(providerId)
                    .build();
            memberRepository.save(member);
        }

        return oAuth2User;
    }
}
