package com.project.blackjack.domain.game.entity;

import lombok.Getter;

@Getter
public class Dealer extends Participant {
    private Hand hand;

    public Dealer() {
        super("Dealer");
    }

    public void initializeHand() {
        this.hand = new Hand(0);
    }

    public boolean mustHit() {
        return hand.calculateScore() <= 16;
    }

    public Card getUpCard() {
        if(hand.getCards().isEmpty()) return null;
        return hand.getCards().get(0);
    }
}
