package com.project.blackjack.domain.member.entity;

import com.project.blackjack.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 50)
    private String username;

    @Column(length = 100)
    private String password;

    @Column(unique = true, nullable = false, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private Long money;

    private String provider;
    private String providerId;

    @Builder
    public Member(String username, String password, String nickname, Role role, String provider, String providerId, Long money) {
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
        this.provider = provider;
        this.providerId = providerId;
        this.money = money != null ? money : 0L;
    }

    public void addMoney(Long amount) {
        this.money += amount;
    }

    public void subtractMoney(Long amount) {
        if(this.money < amount) {
            throw new IllegalArgumentException("소지금이 부족합니다.");
        }
        this.money -= amount;
    }
}
