package com.project.blackjack.domain.game.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GameErrorResponse {
    private String username;
    private String message;
}
