package com.project.blackjack.domain.game.entity;

import lombok.Getter;

@Getter
public class Participant {
    protected String name;

    public Participant(String name) {
        this.name = name;
    }
}
