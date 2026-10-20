package com.project.blackjack.domain.game.service;

import com.project.blackjack.domain.game.dto.GameStatusResponse;
import com.project.blackjack.domain.game.entity.*;
import com.project.blackjack.domain.member.entity.Member;
import com.project.blackjack.domain.member.repository.MemberRepository;
import com.project.blackjack.domain.room.entity.Room;
import com.project.blackjack.domain.room.repository.RoomManager;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {
    private final RoomManager roomManager;
    private final MemberRepository members;
    private final SimpMessagingTemplate messaging;
    private final TransactionTemplate transactions;
    private final com.project.blackjack.domain.room.repository.RoomCodec codec;
    private final com.project.blackjack.domain.game.repository.ReceiptRepository receipts;
    private volatile boolean stopping;
    @org.springframework.context.event.EventListener(org.springframework.context.event.ContextClosedEvent.class)
    public void stopping() { stopping=true; }
    private final ScheduledExecutorService timers = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "blackjack-timer"); t.setDaemon(true); return t;
    });
    @PreDestroy public void close() { timers.shutdownNow(); }
    private void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    private Room room(String id) {
        Room r = roomManager.findById(id);
        require(r != null, "방을 찾을 수 없습니다."); return r;
    }
    // Snapshot and wallet changes commit together. Restore memory on any rollback.
    private void change(String id, Consumer<Room> operation) {
        require(!stopping, "서버가 재시작 중입니다. 잠시 후 다시 시도해주세요.");
        Room r=room(id);
        synchronized(r) {
            String before=codec.encode(r);
            try {
                transactions.executeWithoutResult(tx -> {
                    operation.accept(r);
                    if(r.getGame()!=null) r.getGame().setRevision(r.getGame().getRevision()+1);
                    roomManager.persist(r);
                });
            } catch(RuntimeException ex) {
                codec.restore(r,before); scheduleCommitted(r); throw ex;
            }
            scheduleCommitted(r);
            broadcast(r);
        }
    }
    public void joinObserver(String id,String name) { change(id,r -> r.joinRoom(name)); }
    public void resume(String id,String name) {
        change(id,r -> {
            require(r.isPaused() && r.getGame()!=null,"복구 대기 중인 게임이 아닙니다.");
            require(r.getGame().getPlayers().stream().anyMatch(p -> p.getName().equals(name)) || r.getSeats().containsValue(name),"기존 참가자만 게임을 재개할 수 있습니다.");
            r.setPaused(false);
            long delay=switch(r.getGame().getState()) { case BETTING, INSURANCE -> 10000; case PLAYER_TURN -> 20000; case FINISHED -> 5000; case DEALER_TURN -> 1000; default -> 0; };
            r.getGame().setDeadline(delay==0?0:System.currentTimeMillis()+delay);
        });
    }
    private Member member(String name) { return members.findByUsername(name).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다.")); }
    private BlackjackGame game(Room r, GameState expected) {
        require(!r.isPaused(), "복구된 게임을 먼저 재개해주세요.");
        require(r.getGame() != null && r.getGame().getState() == expected, "현재 단계에서 사용할 수 없는 요청입니다."); return r.getGame();
    }
    private Player player(BlackjackGame g, String name) {
        return g.getPlayers().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow(() -> new IllegalArgumentException("이번 라운드 참가자가 아닙니다."));
    }
    public void requireMember(String id, String name) { require(name != null && room(id).contains(name), "방에 먼저 입장해주세요."); }
    public void sitDown(String id, String name, int seat) {
        change(id, r -> {
            require(r.contains(name), "방에 먼저 입장해주세요.");
            require(member(name).getMoney() >= r.getMinBet(), "최소 베팅에 필요한 잔액이 부족합니다.");
            require(r.takeSeat(name, seat), "유효하지 않거나 이미 사용 중인 자리입니다.");
        });
    }
    public void setupGame(String id, String name) {
        change(id, r -> {
            require(r.getSeats().containsValue(name), "착석한 플레이어만 시작할 수 있습니다.");
            require(r.getGame() == null || r.getGame().getState() == GameState.WAITING, "이미 진행 중인 게임입니다.");
            if (r.getGame() == null) {
                Dealer d = new Dealer(); d.initializeHand();
                r.setGame(new BlackjackGame(new Deck(), d, new ArrayList<>()));
            }
            beginRound(r);
        });
    }
    private void beginRound(Room r) {
        BlackjackGame g = r.getGame(); r.setPaused(false); g.resetForNextRound(); g.setSettled(false); g.setRoundId(g.getRoundId() + 1);
        List<Player> players = new ArrayList<>();
        for (var entry : new TreeMap<>(r.getSeats()).entrySet()) {
            Member m = member(entry.getValue());
            if (m.getMoney() < r.getMinBet()) {
                r.getSeats().remove(entry.getKey()); r.getObservers().add(m.getUsername());
            } else players.add(new Player(m.getUsername(), m.getMoney()));
        }
        g.setPlayers(players);
        if (players.isEmpty()) { g.setState(GameState.WAITING); g.setDeadline(0); return; }
        g.setState(GameState.BETTING);
        later(r, 10000, () -> deal(r));
    }
    // Set a durable deadline only; scheduling is performed after the DB commit.
    private void later(Room r,long delay,Runnable ignored) { r.getGame().setDeadline(System.currentTimeMillis()+delay); }
    private void scheduleCommitted(Room r) {
        BlackjackGame g=r.getGame();
        if(stopping || r.isPaused() || g==null || g.getDeadline()==0 || g.getState()==GameState.WAITING) return;
        long revision=g.getRevision(), deadline=g.getDeadline();
        timers.schedule(() -> {
            try {
                synchronized(r) {
                    if(stopping || roomManager.findById(r.getRoomId())!=r || r.isPaused() || r.getGame()!=g || g.getRevision()!=revision || g.getDeadline()!=deadline) return;
                    change(r.getRoomId(),this::timeout);
                }
            } catch(Exception e) { log.error("게임 타이머 처리 실패: {}",r.getRoomId(),e); }
        },Math.max(1,deadline-System.currentTimeMillis()),TimeUnit.MILLISECONDS);
    }
    private void timeout(Room r) {
        BlackjackGame g=r.getGame();
        switch(g.getState()) {
            case BETTING -> deal(r);
            case INSURANCE -> { g.getPlayers().forEach(p -> g.getInsuranceDecisions().putIfAbsent(p.getName(),false));afterInsurance(r); }
            case PLAYER_TURN -> { g.getCurrentPlayer().getHands().stream().filter(h -> h.getStatus()==HandStatus.PLAYING).findFirst().ifPresent(Hand::stand);advance(r); }
            case DEALER_TURN -> dealerStep(r);
            case FINISHED -> beginRound(r);
            default -> g.setDeadline(0);
        }
    }
    public void placeBet(String id, String name, long amount) {
        change(id, r -> {
            BlackjackGame g = game(r, GameState.BETTING); player(g, name);
            require(amount >= r.getMinBet() && amount <= 1000000000L && amount % 100 == 0, "베팅은 최소 베팅 이상, 100 단위여야 합니다.");
            require(amount <= member(name).getMoney(), "잔액이 부족합니다.");
            require(!g.getRoundBets().containsKey(name), "이미 베팅했습니다.");
            g.getRoundBets().put(name, amount);
            if (g.getRoundBets().size() == g.getPlayers().size()) deal(r);
        });
    }
    private void deal(Room r) {
        BlackjackGame g = game(r, GameState.BETTING);
        if (g.getPlayers().isEmpty()) { g.setState(GameState.WAITING); g.setDeadline(0); return; }
        for (Player p : g.getPlayers()) {
            long bet = g.getRoundBets().getOrDefault(p.getName(), r.getMinBet());
            Member m = member(p.getName()); m.subtractMoney(bet); p.setMoney(m.getMoney()); p.initializeHand(bet);
        }
        for (int i = 0; i < 2; i++) {
            for (Player p : g.getPlayers()) p.getHands().get(0).addCard(g.getDeck().draw());
            g.getDealer().getHand().addCard(g.getDeck().draw());
        }
        for (Player p : g.getPlayers()) if (p.getHands().get(0).isNaturalBlackjack()) p.getHands().get(0).stand();
        if (g.getDealer().getUpCard().getDenomination() == Denomination.ACE) {
            g.setState(GameState.INSURANCE);
            later(r, 10000, () -> { for (Player p : g.getPlayers()) g.getInsuranceDecisions().putIfAbsent(p.getName(), false); afterInsurance(r); });
        } else if (g.getDealer().getHand().isNaturalBlackjack()) finish(r);
        else { g.setState(GameState.PLAYER_TURN); advance(r); }
    }
    public void handleInsuranceChoice(String id, String name, boolean accept) {
        change(id, r -> {
            BlackjackGame g = game(r, GameState.INSURANCE); Player p = player(g, name);
            require(!g.getInsuranceDecisions().containsKey(name), "이미 보험 선택을 완료했습니다.");
            if (accept) {
                Member m = member(name); m.subtractMoney(p.getHands().get(0).getBetAmount() / 2);
                p.setMoney(m.getMoney()); p.setHasInsurance(true);
            }
            g.getInsuranceDecisions().put(name, accept);
            if (g.getInsuranceDecisions().size() == g.getPlayers().size()) afterInsurance(r);
        });
    }
    private void afterInsurance(Room r) {
        if (r.getGame().getDealer().getHand().isNaturalBlackjack()) finish(r);
        else { r.getGame().setState(GameState.PLAYER_TURN); advance(r); }
    }
    public void handlePlayerAction(String id, String name, PlayerAction action, int index, long revision) {
        change(id, r -> {
            BlackjackGame g = game(r, GameState.PLAYER_TURN);
            require(g.getRevision() == revision, "게임 상태가 변경되었습니다. 다시 시도해주세요.");
            Player p = g.getCurrentPlayer(); require(p != null && p.getName().equals(name), "본인의 차례가 아닙니다.");
            int active = -1;
            for (int i = 0; i < p.getHands().size(); i++) if (p.getHands().get(i).getStatus() == HandStatus.PLAYING) { active = i; break; }
            require(index >= 0 && index == active && action != null, "진행 중인 패를 선택해주세요.");
            Hand h = p.getHands().get(index); Member m = member(name);
            switch(action) {
                case HIT -> hit(g, h);
                case STAND -> h.stand();
                case DOUBLE_DOWN -> {
                    require(h.getCards().size() == 2, "더블다운은 첫 두 장에서만 가능합니다.");
                    m.subtractMoney(h.getBetAmount()); h.doubleDown(); hit(g, h);
                    if (h.getStatus() != HandStatus.BUSTED) h.stand();
                }
                case SPLIT -> {
                    require(h.canSplit() && p.getHands().size() < 4, "같은 숫자의 두 장만 최대 4패까지 스플릿할 수 있습니다.");
                    m.subtractMoney(h.getBetAmount()); boolean aces = h.getCards().get(0).getDenomination() == Denomination.ACE;
                    p.splitHand(index); Hand other = p.getHands().get(p.getHands().size() - 1);
                    hit(g, h); hit(g, other);
                    if (aces) { h.stand(); other.stand(); }
                }
                case SURRENDER -> {
                    require(h.getCards().size() == 2 && !h.isSplit(), "서렌더는 스플릿 전 첫 두 장에서만 가능합니다.");
                    h.surrender(); // Refund exactly once during final settlement.
                }
            }
            p.setMoney(m.getMoney()); advance(r);
        });
    }
    private void hit(BlackjackGame g, Hand h) {
        h.addCard(g.getDeck().draw());
        if (h.calculateScore() > 21) h.bust(); else if (h.calculateScore() == 21) h.stand();
    }
    private void advance(Room r) {
        BlackjackGame g = r.getGame();
        while (g.getCurrentPlayer() != null && g.getCurrentPlayer().getHands().stream().noneMatch(h -> h.getStatus() == HandStatus.PLAYING))
            g.setCurrentPlayerIndex(g.getCurrentPlayerIndex() + 1);
        if (g.getCurrentPlayer() != null) {
            later(r, 20000, () -> {
                Player p = g.getCurrentPlayer();
                p.getHands().stream().filter(h -> h.getStatus() == HandStatus.PLAYING).findFirst().ifPresent(Hand::stand);
                advance(r);
            });
            return;
        }
        boolean needsDealer = g.getPlayers().stream().flatMap(p -> p.getHands().stream()).anyMatch(h -> h.getStatus() == HandStatus.STOOD && !h.isNaturalBlackjack());
        if (!needsDealer) { finish(r); return; }
        g.setState(GameState.DEALER_TURN); later(r, 1000, () -> dealerStep(r));
    }
    private void dealerStep(Room r) {
        BlackjackGame g = r.getGame();
        if (g.getDealer().mustHit()) { g.getDealer().getHand().addCard(g.getDeck().draw()); later(r, 1000, () -> dealerStep(r)); }
        else finish(r);
    }
    private void finish(Room r) {
        BlackjackGame g = r.getGame(); if (g.isSettled()) return;
        Hand dealer = g.getDealer().getHand(); int score = dealer.calculateScore(); boolean bj = dealer.isNaturalBlackjack();
        for (Player p : g.getPlayers()) {
            Member m = member(p.getName());
            if (p.isHasInsurance() && bj) m.addMoney(p.getHands().get(0).getBetAmount() / 2 * 3);
            for (Hand h : p.getHands()) {
                long payout = 0; String result = "LOSE";
                if (h.getStatus() == HandStatus.SURRENDERED) { payout = h.getBetAmount() / 2; result = "SURRENDER"; }
                else if (h.getStatus() == HandStatus.BUSTED) result = "BUST";
                else if (h.isNaturalBlackjack()) { payout = bj ? h.getBetAmount() : h.getBetAmount() * 5 / 2; result = bj ? "PUSH" : "BLACKJACK"; }
                else if (!bj && (score > 21 || h.calculateScore() > score)) { payout = h.getBetAmount() * 2; result = "WIN"; }
                else if (!bj && h.calculateScore() == score) { payout = h.getBetAmount(); result = "PUSH"; }
                h.setResult(result); h.setPayout(payout); m.addMoney(payout);
            }
            p.setMoney(m.getMoney());
            long stake=p.getHands().stream().mapToLong(Hand::getBetAmount).sum()+(p.isHasInsurance()?g.getRoundBets().getOrDefault(p.getName(),r.getMinBet())/2:0);
            long paid=p.getHands().stream().mapToLong(Hand::getPayout).sum()+(p.isHasInsurance()&&bj?g.getRoundBets().getOrDefault(p.getName(),r.getMinBet())/2*3:0);
            receipts.save(new com.project.blackjack.domain.game.repository.RoundReceipt(r.getRoomId(),g.getRoundId(),p.getName(),stake,paid,m.getMoney()));
        }
        g.setSettled(true); g.setState(GameState.FINISHED);
        later(r, 5000, () -> beginRound(r));
    }
    public void handlePlayerLeave(String id, String name) {
        change(id, r -> {
            r.leaveRoom(name); BlackjackGame g = r.getGame(); if (g == null) return;
            boolean wasPaused=r.isPaused(); r.setPaused(false);
            if (g.getState() == GameState.BETTING) {
                g.getPlayers().removeIf(p -> p.getName().equals(name)); g.getRoundBets().remove(name);
                if (g.getPlayers().isEmpty() || g.getRoundBets().size() == g.getPlayers().size()) deal(r);
            } else if (g.getState() == GameState.INSURANCE || g.getState() == GameState.PLAYER_TURN) {
                if (g.getPlayers().stream().noneMatch(p -> p.getName().equals(name))) { r.setPaused(wasPaused); return; }
                g.getPlayers().stream().filter(p -> p.getName().equals(name)).forEach(p -> p.getHands().stream().filter(h -> h.getStatus() == HandStatus.PLAYING).forEach(Hand::stand));
                if (g.getState() == GameState.INSURANCE) {
                    g.getInsuranceDecisions().putIfAbsent(name, false);
                    if (g.getInsuranceDecisions().size() == g.getPlayers().size()) afterInsurance(r);
                } else advance(r);
            }
            if (r.getTotalPlayerCount() == 0 && !g.isSettled() && g.getState() != GameState.WAITING && g.getState() != GameState.BETTING) {
                while (!g.getDealer().getHand().isNaturalBlackjack() && g.getDealer().mustHit()) g.getDealer().getHand().addCard(g.getDeck().draw());
                finish(r);
            }
            if(wasPaused && r.getTotalPlayerCount()>0) r.setPaused(true);
        });
    }
    public void syncGameState(String id) { Room r = room(id); synchronized(r) { broadcast(r); } }
    private void broadcast(Room r) {
        GameStatusResponse response = new GameStatusResponse(r.getRoomId(), r.getGame(), r.getSeats());
        response.setPaused(r.isPaused()); response.setTitle(r.getTitle());
        response.setMinBet(r.getMinBet()); response.setMaxPlayers(r.getMaxPlayers());
        messaging.convertAndSend("/topic/room/" + r.getRoomId(), response);
    }
}
