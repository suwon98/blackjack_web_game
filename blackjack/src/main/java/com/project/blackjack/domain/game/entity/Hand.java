package com.project.blackjack.domain.game.entity;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class Hand {
    private final List<Card> cards = new ArrayList<>();
    private HandStatus status = HandStatus.PLAYING;
    private long betAmount;

    public Hand(long betAmount) {
        this.betAmount = betAmount;
    }

    public void addCard(Card card) {
        cards.add(card);
    }

    public int calculateScore() {
        int score = 0;
        int aceCount = 0;

        for(Card card : cards) {
            score += card.getDenomination().getScore();
            if(card.getDenomination() == Denomination.ACE) {
                aceCount++;
            }
        }

        while(aceCount > 0 && score + 10 <= 21) {
            score += 10;
            aceCount--;
        }

        return score;
    }

    public void stand() {this.status = HandStatus.STOOD;}
    public void bust() {this.status = HandStatus.BUSTED;}
    public void surrender() {this.status = HandStatus.SURRENDERED;}

    public void doubleDown() {
        this.betAmount *= 2;
        this.status = HandStatus.DOUBLE_DOWN;
    }

    public boolean canSplit() {
        return cards.size() == 2 &&
                cards.get(0).getDenomination() == cards.get(1).getDenomination();
    }

    public Card removeSecondCardForSplit() {
        if(!canSplit()) {
            throw new IllegalStateException("스플릿할 수 없는 패입니다.");
        }
        return cards.remove(1);
    }
}
