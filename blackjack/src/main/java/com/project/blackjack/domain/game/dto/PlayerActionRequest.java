package com.project.blackjack.domain.game.dto;

import com.project.blackjack.domain.game.entity.PlayerAction;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PlayerActionRequest {
    private String roomId;
    private String username;
    private PlayerAction action;
    private int handIndex;
}
