package com.project.blackjack.domain.game.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class Card {
    private final Suit suit;
    private final Denomination denomination;

    @Override
    public String toString() {
        return suit.getName() + " " + denomination.getSymbol();
    }
}
