package com.project.blackjack.domain.game.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class GameStartRequest {
    private String roomId;
    private String username;
    private List<String> usernames;
}
