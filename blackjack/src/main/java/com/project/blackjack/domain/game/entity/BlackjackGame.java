package com.project.blackjack.domain.game.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class BlackjackGame {
    private Deck deck;
    private Dealer dealer;
    private List<Player> players;
    private GameState state;
    private int currentPlayerIndex;
    private long revision;
    private long deadline;
    private long roundId;
    private boolean settled;

    private Map<String, Long> roundBets = new ConcurrentHashMap<>();
    private Map<String, Boolean> insuranceDecisions = new ConcurrentHashMap<>();

    public BlackjackGame(Deck deck, Dealer dealer, List<Player> players) {
        this.deck = deck;
        this.dealer = dealer;
        this.players = players;
        this.state = GameState.WAITING;
        this.currentPlayerIndex = 0;
    }

    public Player getCurrentPlayer() {
        if(currentPlayerIndex >= players.size()) return null;
        return players.get(currentPlayerIndex);
    }

    public void resetForNextRound() {
        if(this.deck.isReshuffleNeeded()) {
            this.deck = new Deck();
        }
        this.dealer.getHand().getCards().clear();
        for(Player p : players) {
            p.getHands().clear();
            p.setHasInsurance(false);
        }
        this.state = GameState.WAITING;
        this.currentPlayerIndex = 0;
        this.roundBets.clear();
        this.insuranceDecisions.clear();
    }
}