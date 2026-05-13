package com.project.blackjack.domain.game.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Player extends Participant {
    private final List<Hand> hands = new ArrayList<>();
    private long money;

    private boolean hasInsurance = false;

    public Player(String name, long money) {
        super(name);
        this.money = money;
    }

    public void initializeHand(long initialBet) {
        hands.clear();
        hands.add(new Hand(initialBet));
        this.hasInsurance = false;
    }

    public void doubleDownHand(int handIndex) {
        Hand targetHand = hands.get(handIndex);
        targetHand.doubleDown();
    }

    public void splitHand(int handIndex) {
        Hand targetHand = hands.get(handIndex);
        long requiredBet = targetHand.getBetAmount();
        Card splitCard = targetHand.removeSecondCardForSplit();

        Hand newHand = new Hand(requiredBet);
        newHand.addCard(splitCard);

        hands.add(newHand);
    }
}