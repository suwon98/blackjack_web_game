package com.project.blackjack.domain.member.dto;

import com.project.blackjack.domain.member.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemberInfoResponse {
    private String username;
    private String nickname;
    private Role role;
    private Long money;
}
