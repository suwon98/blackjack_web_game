package com.project.blackjack.domain.game.entity;

public enum GameState {
    WAITING,
    BETTING,
    DEALING,
    INSURANCE,
    DEALER_PEEK,
    PLAYER_TURN,
    DEALER_TURN,
    FINISHED
}
