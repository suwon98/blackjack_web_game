package com.project.blackjack.domain.game.dto;

import com.project.blackjack.domain.game.entity.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class GameStatusResponse {
    private final String roomId;
    private final GameState state;
    private final int currentPlayerIndex;
    private final DealerDto dealer;
    private final List<PlayerDto> players;
    private final Map<Integer, String> seats;
    private final int remainingCards;

    public GameStatusResponse(String roomId, BlackjackGame game, Map<Integer, String> seats) {
        this.roomId = roomId;
        this.seats = seats;

        if (game != null) {
            this.state = game.getState();
            this.currentPlayerIndex = game.getCurrentPlayerIndex();
            this.dealer = new DealerDto(game.getDealer(), this.state);
            this.players = game.getPlayers().stream()
                    .map(PlayerDto::new)
                    .collect(Collectors.toList());

            if (game.getDeck() != null && game.getDeck().getCards() != null) {
                this.remainingCards = game.getDeck().getCards().size();
            } else {
                this.remainingCards = 0;
            }
        } else {
            this.state = GameState.WAITING;
            this.currentPlayerIndex = 0;
            this.dealer = null;
            this.players = new ArrayList<>();
            this.remainingCards = 0;
        }
    }

    @Getter
    public static class DealerDto {
        private final List<CardDto> cards;
        private final int score;

        public DealerDto(Dealer dealer, GameState state) {
            List<Card> actualCards = dealer.getHand().getCards();
            if ((state == GameState.DEALING || state == GameState.INSURANCE ||
                    state == GameState.DEALER_PEEK || state == GameState.PLAYER_TURN)
                    && actualCards.size() >= 2) {
                this.cards = List.of(new CardDto(actualCards.get(0)), new CardDto(true));
                this.score = actualCards.get(0).getDenomination().getScore();
            } else {
                this.cards = actualCards.stream().map(CardDto::new).collect(Collectors.toList());
                this.score = dealer.getHand().calculateScore();
            }
        }
    }

    @Getter
    public static class PlayerDto {
        private final String name;
        private final long money;
        private final boolean hasInsurance;
        private final List<HandDto> hands;

        public PlayerDto(Player player) {
            this.name = player.getName();
            this.money = player.getMoney();
            this.hasInsurance = player.isHasInsurance();
            this.hands = player.getHands().stream().map(HandDto::new).collect(Collectors.toList());
        }
    }

    @Getter
    public static class HandDto {
        private final List<CardDto> cards;
        private final long betAmount;
        private final int score;
        private final String status;

        public HandDto(Hand hand) {
            this.cards = hand.getCards().stream().map(CardDto::new).collect(Collectors.toList());
            this.betAmount = hand.getBetAmount();
            this.score = hand.calculateScore();
            this.status = hand.getStatus().name();
        }
    }

    @Getter
    public static class CardDto {
        private final String suit;
        private final String denomination;
        private final boolean isHidden;

        public CardDto(Card card) {
            this.suit = card.getSuit().name();
            this.denomination = card.getDenomination().name();
            this.isHidden = false;
        }

        public CardDto(boolean isHidden) {
            this.isHidden = isHidden;
            this.suit = "HIDDEN";
            this.denomination = "HIDDEN";
        }
    }
}