package com.project.blackjack.domain.game.service;

import com.project.blackjack.domain.game.dto.GameStatusResponse;
import com.project.blackjack.domain.game.entity.*;
import com.project.blackjack.domain.member.entity.Member;
import com.project.blackjack.domain.member.repository.MemberRepository;
import com.project.blackjack.domain.room.entity.Room;
import com.project.blackjack.domain.room.repository.RoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {
    private final RoomManager roomManager;
    private final MemberRepository memberRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, Long> roomBettingTimestamps = new ConcurrentHashMap<>();

    @Lazy
    @Autowired
    private GameService self;

    @Transactional
    public void sitDown(String roomId, String username, int seatIndex) {
        Room room = roomManager.findById(roomId);
        if(room == null) return;

        boolean success = room.takeSeat(username, seatIndex);
        if(!success) {
            throw new IllegalArgumentException("이미 다른 유저가 앉아있거나 유효하지 않은 자리입니다.");
        }

        broadcastGameState(roomId, room.getGame());
    }

    @Transactional
    public void setupGame(String roomId) {
        Room room = roomManager.findById(roomId);
        if(room == null) throw new IllegalArgumentException("방을 찾을 수 없습니다.");

        Deck deck = new Deck();
        Dealer dealer = new Dealer();
        dealer.initializeHand();

        List<String> usernames = room.getSeats().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        if (usernames.isEmpty()) {
            throw new IllegalArgumentException("자리에 앉은 플레이어가 없습니다. (관전 상태)");
        }

        List<Player> gamePlayers = usernames.stream().map(name -> {
            Member m = memberRepository.findByUsername(name).orElseThrow();
            return new Player(m.getUsername(), m.getMoney());
        }).collect(Collectors.toList());

        BlackjackGame game = new BlackjackGame(deck, dealer, gamePlayers);
        room.setGame(game);

        game.setState(GameState.BETTING);
        broadcastGameState(roomId, game);
        long timerId = System.currentTimeMillis();
        roomBettingTimestamps.put(roomId, timerId);
        self.startBettingTimer(roomId, timerId);
    }

    @Transactional
    public void startNextRound(String roomId) {
        Room room = roomManager.findById(roomId);
        if(room == null || room.getGame() == null) throw new IllegalArgumentException("진행 중인 게임이 없습니다.");

        BlackjackGame game = room.getGame();
        game.resetForNextRound();

        List<Player> newPlayers = room.getSeats().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String name = entry.getValue();
                    Member m = memberRepository.findByUsername(name).orElseThrow();
                    return new Player(m.getUsername(), m.getMoney());
                }).collect(Collectors.toList());

        game.setPlayers(newPlayers);

        game.setState(GameState.BETTING);
        broadcastGameState(roomId, game);
        long timerId = System.currentTimeMillis();
        roomBettingTimestamps.put(roomId, timerId);
        self.startBettingTimer(roomId, timerId);
    }

    @Transactional
    public void placeBet(String roomId, String username, long betAmount) {
        Room room = roomManager.findById(roomId);
        if (room == null || room.getGame() == null) return;

        BlackjackGame game = room.getGame();

        if (game.getState() != GameState.BETTING) {
            throw new IllegalStateException("지금은 배팅 시간이 아닙니다.");
        }

        game.getRoundBets().put(username, betAmount);

        if (game.getRoundBets().size() == game.getPlayers().size()) {
            self.executeDealing(roomId);
        } else {
            broadcastGameState(roomId, game);
        }
    }

    @Async
    public void startBettingTimer(String roomId, long timerId) {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Long currentTimerId = roomBettingTimestamps.get(roomId);
        if (currentTimerId != null && currentTimerId == timerId) {
            self.executeDealing(roomId);
        }
    }

    @Transactional
    public void executeDealing(String roomId) {
        Room room = roomManager.findById(roomId);
        if (room == null || room.getGame() == null) return;

        BlackjackGame game = room.getGame();
        if (game.getState() != GameState.BETTING) return;

        for (Player p : game.getPlayers()) {
            Member m = memberRepository.findByUsername(p.getName()).orElseThrow();

            long betAmount = game.getRoundBets().getOrDefault(p.getName(), room.getMinBet());

            if (m.getMoney() < betAmount) {
                betAmount = m.getMoney();
            }

            m.subtractMoney(betAmount);
            p.setMoney(m.getMoney());
            p.initializeHand(betAmount);
        }

        game.setState(GameState.DEALING);
        dealInitialCards(game.getPlayers(), game.getDealer(), game.getDeck());
        evaluateNextStateAfterDealing(room);
        broadcastGameState(roomId, game);
    }

    private void dealInitialCards(List<Player> players, Dealer dealer, Deck deck) {
        for(Player p : players) p.getHands().get(0).addCard(deck.draw());
        dealer.getHand().addCard(deck.draw());

        for(Player p : players) p.getHands().get(0).addCard(deck.draw());
        dealer.getHand().addCard(deck.draw());
    }

    public void evaluateNextStateAfterDealing(Room room) {
        BlackjackGame game = room.getGame();
        Dealer dealer = game.getDealer();
        Card upCard = dealer.getHand().getCards().get(0);

        for (Player p : game.getPlayers()) {
            for (Hand h : p.getHands()) {
                if (h.calculateScore() == 21) {
                    h.stand();
                }
            }
        }

        if(upCard.getDenomination() == Denomination.ACE) {
            game.setState(GameState.INSURANCE);
        } else if(upCard.getDenomination().getScore() == 10) {
            game.setState(GameState.DEALER_PEEK);
            checkDealerBlackjack(room);
        } else {
            game.setState(GameState.PLAYER_TURN);
            checkAndProceedTurn(room);
        }
    }

    private void checkDealerBlackjack(Room room) {
        BlackjackGame game = room.getGame();
        if(game.getDealer().getHand().calculateScore() == 21) {
            game.setState(GameState.FINISHED);
            settleGame(game);

            broadcastGameState(room.getRoomId(), game);
            self.scheduleNextRound(room.getRoomId());
        } else {
            game.setState(GameState.PLAYER_TURN);
            checkAndProceedTurn(room);
        }
    }

    @Transactional
    public void handleInsuranceChoice(String roomId, String username, boolean accept) {
        Room room = roomManager.findById(roomId);
        if (room == null || room.getGame() == null) return;
        BlackjackGame game = room.getGame();

        if (game.getState() != GameState.INSURANCE) {
            throw new IllegalStateException("지금은 보험을 가입하는 단계가 아닙니다.");
        }

        Player player = game.getPlayers().stream()
                .filter(p -> p.getName().equals(username))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("게임을 진행 중인 유저가 아닙니다."));

        if (accept) {
            Member member = memberRepository.findByUsername(username).orElseThrow();
            long insuranceBet = player.getHands().get(0).getBetAmount() / 2;
            member.subtractMoney(insuranceBet);
            player.setMoney(member.getMoney());
            player.setHasInsurance(true);
        }

        game.getInsuranceDecisions().put(username, accept);

        if (game.getInsuranceDecisions().size() == game.getPlayers().size()) {
            proceedAfterInsurance(room);
        } else {
            broadcastGameState(roomId, game);
        }
    }

    private void proceedAfterInsurance(Room room) {
        BlackjackGame game = room.getGame();
        Dealer dealer = game.getDealer();

        boolean isDealerBlackjack = dealer.getHand().calculateScore() == 21 && dealer.getHand().getCards().size() == 2;

        if (isDealerBlackjack) {
            game.setState(GameState.FINISHED);
            settleInsurance(game);
            calculateFinalSettlement(game);
            broadcastGameState(room.getRoomId(), game);

            self.scheduleNextRound(room.getRoomId());
        } else {
            game.setState(GameState.PLAYER_TURN);
            checkAndProceedTurn(room);
        }
    }

    private void settleInsurance(BlackjackGame game) {
        for(Player player : game.getPlayers()) {
            if(player.isHasInsurance()) {
                Member member = memberRepository.findByUsername(player.getName()).orElseThrow();
                long originalBet = player.getHands().get(0).getBetAmount();
                member.addMoney(originalBet);
                player.setMoney(member.getMoney());
            }
        }
    }

    @Transactional
    public void handlePlayerAction(String roomId, String username, PlayerAction action, int handIndex) {
        Room room = roomManager.findById(roomId);
        BlackjackGame game = room.getGame();
        Player currentPlayer = game.getCurrentPlayer();
        Hand targetHand = currentPlayer.getHands().get(handIndex);
        Member member = memberRepository.findByUsername(username).orElseThrow();

        switch (action) {
            case HIT:
                targetHand.addCard(game.getDeck().draw());
                if(targetHand.calculateScore() > 21) targetHand.bust();
                else if(targetHand.calculateScore() == 21) targetHand.stand();
                break;

            case STAND:
                targetHand.stand();
                break;

            case DOUBLE_DOWN:
                long doubleBet = targetHand.getBetAmount();
                member.subtractMoney(doubleBet);
                currentPlayer.setMoney(member.getMoney());
                currentPlayer.doubleDownHand(handIndex);
                targetHand.addCard(game.getDeck().draw());
                if(targetHand.calculateScore() > 21) targetHand.bust();
                else targetHand.stand();
                break;

            case SPLIT:
                long splitBet = targetHand.getBetAmount();
                member.subtractMoney(splitBet);
                currentPlayer.setMoney(member.getMoney());
                currentPlayer.splitHand(handIndex);
                currentPlayer.getHands().get(handIndex).addCard(game.getDeck().draw());
                currentPlayer.getHands().get(currentPlayer.getHands().size() - 1).addCard(game.getDeck().draw());
                break;

            case SURRENDER:
                targetHand.surrender();
                member.addMoney(targetHand.getBetAmount() / 2);
                currentPlayer.setMoney(member.getMoney());
                break;
        }

        checkAndProceedTurn(room);
    }

    private void checkAndProceedTurn(Room room) {
        BlackjackGame game = room.getGame();

        while (game.getCurrentPlayerIndex() < game.getPlayers().size()) {
            Player currentPlayer = game.getCurrentPlayer();
            boolean allHandsFinished = currentPlayer.getHands().stream()
                    .allMatch(h -> h.getStatus() != HandStatus.PLAYING);

            if (allHandsFinished) {
                game.setCurrentPlayerIndex(game.getCurrentPlayerIndex() + 1);
            } else {
                return;
            }
        }

        boolean requiresDealerTurn = game.getPlayers().stream()
                .flatMap(p -> p.getHands().stream())
                .anyMatch(h -> h.getStatus() == HandStatus.STOOD && !(h.calculateScore() == 21 && h.getCards().size() == 2));

        if (!requiresDealerTurn) {
            game.setState(GameState.FINISHED);
            calculateFinalSettlement(game);
            broadcastGameState(room.getRoomId(), game);
            self.scheduleNextRound(room.getRoomId());
        } else {
            game.setState(GameState.DEALER_TURN);
            self.playDealerTurn(room.getRoomId());
        }
    }

    @Transactional
    @Async
    public void playDealerTurn(String roomId) {
        Room room = roomManager.findById(roomId);
        BlackjackGame game = room.getGame();
        Dealer dealer = game.getDealer();
        Deck deck = game.getDeck();
        Hand dealerHand = dealer.getHand();

        try {
            broadcastGameState(roomId, game);
            Thread.sleep(1500);

            while (dealerHand.calculateScore() <= 16) {
                dealerHand.addCard(deck.draw());
                broadcastGameState(roomId, game);
                Thread.sleep(1500);
            }

            game.setState(GameState.FINISHED);
            calculateFinalSettlement(game);
            broadcastGameState(roomId, game);

            self.scheduleNextRound(roomId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void broadcastGameState(String roomId, BlackjackGame game) {
        Room room = roomManager.findById(roomId);
        Map<Integer, String> currentSeats = (room != null) ? room.getSeats() : null;

        GameStatusResponse response = new GameStatusResponse(roomId, game, currentSeats);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, response);
    }

    private void calculateFinalSettlement(BlackjackGame game) {
        int dealerScore = game.getDealer().getHand().calculateScore();
        boolean isDealerBust = dealerScore > 21;
        boolean isDealerBlackjack = dealerScore == 21 && game.getDealer().getHand().getCards().size() == 2;

        for(Player player : game.getPlayers()) {
            Member member = memberRepository.findByUsername(player.getName()).orElseThrow();

            for(Hand hand : player.getHands()) {
                HandStatus status = hand.getStatus();
                if(status == HandStatus.BUSTED || status == HandStatus.SURRENDERED) continue;

                long betAmount = hand.getBetAmount();
                int playerScore = hand.calculateScore();
                boolean isPlayerBlackjack = playerScore == 21 && hand.getCards().size() == 2;

                if (isPlayerBlackjack) {
                    if (isDealerBlackjack) {
                        member.addMoney(betAmount);
                    } else {
                        member.addMoney(betAmount + (long)(betAmount * 1.5));
                    }
                } else if (isDealerBust) {
                    member.addMoney(betAmount * 2);
                } else {
                    if (playerScore > dealerScore) {
                        member.addMoney(betAmount * 2);
                    } else if (playerScore == dealerScore) {
                        member.addMoney(betAmount);
                    }
                }
            }
            player.setMoney(member.getMoney());
        }
    }

    @Async
    public void scheduleNextRound(String roomId) {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Room room = roomManager.findById(roomId);
        if (room != null && room.getGame() != null) {
            if (!room.getSeats().isEmpty()) {
                self.startNextRound(roomId);
            } else {
                room.getGame().setState(GameState.WAITING);
                broadcastGameState(roomId, room.getGame());
            }
        }
    }

    @Transactional
    public void handlePlayerLeave(String roomId, String username) {
        Room room = roomManager.findById(roomId);
        if(room == null || room.getGame() == null) return;

        BlackjackGame game = room.getGame();

        if(game.getState() == GameState.WAITING || game.getState() == GameState.FINISHED) return;

        if(game.getState() == GameState.BETTING) {
            game.getRoundBets().remove(username);
            if(!game.getPlayers().isEmpty() && game.getRoundBets().size() == room.getSeats().size() - 1) {
                self.executeDealing(roomId);
            }
            return;
        }

        Player targetPlayer = game.getPlayers().stream()
                .filter(p -> p.getName().equals(username))
                .findFirst()
                .orElse(null);

        if(targetPlayer != null) {
            for(Hand hand : targetPlayer.getHands()) {
                if(hand.getStatus() == HandStatus.PLAYING) {
                    hand.bust();
                }
            }

            checkAndProceedTurn(room);
        }
    }

    public void syncGameState(String roomId) {
        Room room = roomManager.findById(roomId);
        if (room != null) {
            broadcastGameState(roomId, room.getGame());
        }
    }

    private void settleGame(BlackjackGame game) {
        calculateFinalSettlement(game);
    }
}