package com.project.blackjack.domain.game.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BetRequest {
    private String roomId;
    private String username;
    private long betAmount;
}
