package com.project.blackjack;
import com.project.blackjack.domain.game.entity.*;
import com.project.blackjack.domain.game.service.GameService;
import com.project.blackjack.domain.member.entity.*;
import com.project.blackjack.domain.member.repository.MemberRepository;
import com.project.blackjack.domain.room.entity.Room;
import com.project.blackjack.domain.room.repository.RoomManager;
import com.project.blackjack.domain.room.service.RoomService;
import com.project.blackjack.global.security.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@ActiveProfiles("test")
class GameServiceTests {
    @Autowired GameService games;
    @Autowired RoomService roomService;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean RoomManager rooms;
    @Autowired MemberRepository members;
    @Autowired JwtTokenProvider tokens;
    Room room; String user;
    @BeforeEach void setup() {
        user = "u" + UUID.randomUUID().toString().substring(0, 8);
        members.save(Member.builder().username(user).nickname(user).role(Role.USER).money(100000L).build());
        room = Room.create("test", 2, false, null, 1000); room.joinRoom(user); rooms.save(room);
        games.sitDown(room.getRoomId(), user, 1); games.setupGame(room.getRoomId(), user);
    }
    @AfterEach void cleanup() { rooms.delete(room.getRoomId()); }
    void deck(Denomination... cards) {
        var target = room.getGame().getDeck().getCards(); target.clear();
        for (int i = cards.length - 1; i >= 0; i--) target.add(new Card(Suit.SPADE, cards[i]));
    }
    long money() { return members.findByUsername(user).orElseThrow().getMoney(); }
    void bet() { games.placeBet(room.getRoomId(), user, 1000); }
    void action(PlayerAction a) { games.handlePlayerAction(room.getRoomId(), user, a, 0, room.getGame().getRevision()); }
    @Autowired com.project.blackjack.domain.room.repository.RoomCodec codec;
    @Autowired com.project.blackjack.domain.room.repository.RoomRecordRepository records;
    @Test void durableSnapshotRestoresExactDeckAndRequiresExplicitResume() {
        deck(Denomination.TWO,Denomination.TEN,Denomination.THREE,Denomination.SEVEN,Denomination.NINE);
        bet();
        String saved=records.findById(room.getRoomId()).orElseThrow().getPayload();
        assertEquals(codec.encode(room), saved);
        long balance=money();
        rooms.load(); room=rooms.findById(room.getRoomId());
        assertTrue(room.isPaused());
        assertEquals(saved,codec.encode(codec.decode(saved)));
        assertEquals(Denomination.NINE,room.getGame().getDeck().getCards().get(0).getDenomination());
        assertThrows(IllegalArgumentException.class,()->action(PlayerAction.HIT));
        assertEquals(balance,money());
        games.resume(room.getRoomId(),user); action(PlayerAction.HIT);
        assertEquals(14,room.getGame().getPlayers().get(0).getHands().get(0).calculateScore());
        assertEquals(balance,money());
    }
    @Test void settledSnapshotDoesNotPayAgainAfterReload() {
        deck(Denomination.ACE,Denomination.NINE,Denomination.KING,Denomination.SEVEN); bet();
        assertEquals(101500,money()); rooms.load();room=rooms.findById(room.getRoomId());
        assertTrue(room.isPaused());assertTrue(room.getGame().isSettled());
        games.resume(room.getRoomId(),user);
        assertThrows(IllegalArgumentException.class,()->action(PlayerAction.STAND));
        assertEquals(101500,money());
    }
    @Test void failedSnapshotWriteRollsBackWalletAndCards() {
        deck(Denomination.TWO,Denomination.TEN,Denomination.THREE,Denomination.SEVEN);
        String before=codec.encode(room);
        String durable=records.findById(room.getRoomId()).orElseThrow().getPayload();
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated storage failure")).when(rooms).persist(org.mockito.ArgumentMatchers.any(Room.class));
        try {
            assertThrows(IllegalStateException.class,this::bet);
            assertEquals(100000,money()); assertEquals(before,codec.encode(room));
            assertEquals(durable,records.findById(room.getRoomId()).orElseThrow().getPayload());
        } finally { org.mockito.Mockito.doCallRealMethod().when(rooms).persist(org.mockito.ArgumentMatchers.any(Room.class)); }
    }
    @Autowired com.project.blackjack.domain.game.repository.ReceiptRepository receipts;
    @Test void insuranceReceiptUsesOriginalBetAfterDoubleDown() throws Exception {
        deck(Denomination.TWO,Denomination.ACE,Denomination.THREE,Denomination.NINE,Denomination.TWO);
        bet();games.handleInsuranceChoice(room.getRoomId(),user,true);action(PlayerAction.DOUBLE_DOWN);
        long until=System.currentTimeMillis()+4000;
        while(receipts.findTop20ByUsernameOrderByCreatedAtDesc(user).isEmpty() && System.currentTimeMillis()<until) Thread.sleep(20);
        var receipt=receipts.findTop20ByUsernameOrderByCreatedAtDesc(user).get(0);
        assertEquals(2500,receipt.getStake());assertEquals(97500,receipt.getBalance());
    }
    @Test void invalidBetsNeverChangeMoney() {
        for (long amount : new long[]{-1000, 0, 100, 1100000, 1001}) assertThrows(IllegalArgumentException.class, () -> games.placeBet(room.getRoomId(), user, amount));
        assertEquals(100000, money()); assertTrue(room.getGame().getRoundBets().isEmpty());
    }
    @Test void cannotRestartOrUseOtherIdentity() {
        assertThrows(IllegalArgumentException.class, () -> games.setupGame(room.getRoomId(), user));
        assertThrows(IllegalArgumentException.class, () -> games.placeBet(room.getRoomId(), "intruder", 1000));
        deck(Denomination.TEN, Denomination.SIX, Denomination.SEVEN, Denomination.TEN); bet();
        assertThrows(IllegalArgumentException.class, () -> games.handlePlayerAction(room.getRoomId(), "intruder", PlayerAction.STAND, 0, room.getGame().getRevision()));
        assertEquals(HandStatus.PLAYING, room.getGame().getPlayers().get(0).getHands().get(0).getStatus());
    }
    @Test void staleActionCannotHitTwice() {
        deck(Denomination.TWO, Denomination.TEN, Denomination.THREE, Denomination.SEVEN, Denomination.TWO); bet();
        long revision = room.getGame().getRevision(); action(PlayerAction.HIT);
        assertThrows(IllegalArgumentException.class, () -> games.handlePlayerAction(room.getRoomId(), user, PlayerAction.HIT, 0, revision));
        assertEquals(3, room.getGame().getPlayers().get(0).getHands().get(0).getCards().size());
    }
    @Test void naturalBlackjackPaysThreeToTwoOnce() {
        deck(Denomination.ACE, Denomination.NINE, Denomination.KING, Denomination.SEVEN); bet();
        assertEquals(GameState.FINISHED, room.getGame().getState()); assertEquals(101500, money());
        assertThrows(IllegalArgumentException.class, () -> action(PlayerAction.STAND)); assertEquals(101500, money());
    }
    @Test void insuranceReturnsStakeAndTwoToOneProfit() {
        deck(Denomination.TEN, Denomination.ACE, Denomination.SEVEN, Denomination.KING); bet();
        games.handleInsuranceChoice(room.getRoomId(), user, true);
        assertEquals(GameState.FINISHED, room.getGame().getState()); assertEquals(100000, money());
        assertThrows(IllegalArgumentException.class, () -> games.handleInsuranceChoice(room.getRoomId(), user, true));
    }
    @Test void surrenderRefundsHalfExactlyOnce() {
        deck(Denomination.TEN, Denomination.SIX, Denomination.SEVEN, Denomination.TEN); bet(); action(PlayerAction.SURRENDER);
        assertEquals(99500, money()); assertEquals("SURRENDER", room.getGame().getPlayers().get(0).getHands().get(0).getResult());
        assertThrows(IllegalArgumentException.class, () -> action(PlayerAction.SURRENDER));
    }
    @Test void splitHandsAreNotNaturalBlackjacks() {
        deck(Denomination.ACE, Denomination.SIX, Denomination.ACE, Denomination.TEN, Denomination.KING, Denomination.QUEEN); bet(); action(PlayerAction.SPLIT);
        var hands = room.getGame().getPlayers().get(0).getHands();
        assertEquals(2, hands.size()); assertTrue(hands.stream().allMatch(h -> h.calculateScore() == 21 && !h.isNaturalBlackjack() && h.getStatus() == HandStatus.STOOD));
        assertEquals(98000, money());
    }
    @Test void invalidSplitDoesNotDeductMoney() {
        deck(Denomination.TEN, Denomination.SIX, Denomination.SEVEN, Denomination.TEN); bet();
        assertThrows(IllegalArgumentException.class, () -> action(PlayerAction.SPLIT)); assertEquals(99000, money());
    }
    @Test void leavingDuringBettingRemovesPlayerAndStopsRound() {
        roomService.leaveRoom(room.getRoomId(), user);
        assertTrue(room.getGame().getPlayers().isEmpty()); assertEquals(GameState.WAITING, room.getGame().getState()); assertEquals(100000, money());
    }
    @Test void duplicateConcurrentBetsDebitOnlyOnce() throws Exception {
        deck(Denomination.ACE, Denomination.NINE, Denomination.KING, Denomination.SEVEN);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = executor.invokeAll(List.<Callable<Boolean>>of(() -> { try { bet(); return true; } catch (IllegalArgumentException e) { return false; } }, () -> { try { bet(); return true; } catch (IllegalArgumentException e) { return false; } }));
            assertEquals(1, futures.stream().filter(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } }).count());
            assertEquals(101500, money());
        } finally { executor.shutdownNow(); }
    }
    @Test void refreshTokenIsNotAnAccessToken() {
        String token = tokens.createRefreshToken(user); assertTrue(tokens.validateRefreshToken(token)); assertFalse(tokens.validateAccessToken(token));
    }
    @Test void aceScoresAndSixDeckShoe() {
        Hand h = new Hand(1000); h.addCard(new Card(Suit.SPADE, Denomination.ACE)); h.addCard(new Card(Suit.HEART, Denomination.ACE)); h.addCard(new Card(Suit.CLUB, Denomination.NINE));
        assertEquals(21, h.calculateScore()); assertEquals(312, new Deck().getRemainingCards());
    }

    @Test void lastPlayerLeavingStillSettlesBeforeRoomDeletion() {
        deck(Denomination.TEN, Denomination.SIX, Denomination.NINE, Denomination.TEN, Denomination.TEN);
        bet(); roomService.leaveRoom(room.getRoomId(), user);
        assertEquals(GameState.FINISHED, room.getGame().getState()); assertEquals(101000, money());
        assertNull(rooms.findById(room.getRoomId()));
    }
    @Test void doubleDownChargesOnceAndSettlesAtDoubleStake() throws Exception {
        deck(Denomination.FIVE, Denomination.TEN, Denomination.SIX, Denomination.SEVEN, Denomination.KING);
        bet(); action(PlayerAction.DOUBLE_DOWN);
        assertEquals(98000, money());
        long deadline = System.currentTimeMillis() + 4000;
        while (room.getGame().getState() != GameState.FINISHED && System.currentTimeMillis() < deadline) Thread.sleep(25);
        assertEquals(GameState.FINISHED, room.getGame().getState()); assertEquals(102000, money());
    }
    @Test void duplicateInsuranceChoiceCannotChargeTwice() {
        String other = "u" + UUID.randomUUID().toString().substring(0, 8);
        members.save(Member.builder().username(other).nickname(other).role(Role.USER).money(100000L).build());
        room.getGame().getPlayers().add(new Player(other, 100000)); room.joinRoom(other); room.takeSeat(other, 2);
        deck(Denomination.TEN, Denomination.TEN, Denomination.ACE, Denomination.SEVEN, Denomination.EIGHT, Denomination.NINE);
        bet(); games.placeBet(room.getRoomId(), other, 1000);
        games.handleInsuranceChoice(room.getRoomId(), user, true);
        assertEquals(98500, money());
        assertThrows(IllegalArgumentException.class, () -> games.handleInsuranceChoice(room.getRoomId(), user, true));
        assertEquals(98500, money());
    }
}
