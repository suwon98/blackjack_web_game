package com.project.blackjack.domain.game.entity;

import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class Deck {
    private final Stack<Card> cards = new Stack<>();
    private final int cutCardThreshold;

    public Deck() {
        this(6);
    }

    public Deck(int deckCount) {
        for (int i = 0; i < deckCount; i++) {
            for (Suit suit : Suit.values()) {
                for (Denomination denom : Denomination.values()) {
                    cards.push(new Card(suit, denom));
                }
            }
        }

        shuffle();

        this.cutCardThreshold = (int) (cards.size() * 0.25);
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public Card draw() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("덱에 카드가 없습니다.");
        }
        return cards.pop();
    }

    public void burn(int count) {
        for (int i = 0; i < count; i++) {
            if (!cards.isEmpty()) {
                cards.pop();
            }
        }
    }

    public List<Card> getCards() {
        return this.cards;
    }

    public boolean isReshuffleNeeded() {
        return cards.size() <= cutCardThreshold;
    }

    public int getRemainingCards() {
        return cards.size();
    }
}