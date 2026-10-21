package com.project.blackjack.domain.game.service;

import com.project.blackjack.domain.game.entity.GameState;
import com.project.blackjack.domain.member.entity.*;
import com.project.blackjack.domain.member.repository.MemberRepository;
import com.project.blackjack.domain.room.entity.Room;
import com.project.blackjack.domain.room.repository.*;
import com.project.blackjack.domain.room.service.RoomService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class WaitingRoomTests {
    @Autowired GameService games;
    @Autowired RoomManager rooms;
    @Autowired RoomCodec codec;
    @Autowired RoomService roomService;
    @Autowired MemberRepository members;
    Room room; String host, a, b;
    String member() {
        String n="w"+UUID.randomUUID().toString().substring(0,8);
        members.save(Member.builder().username(n).nickname(n).role(Role.USER).money(100000L).build());return n;
    }
    @BeforeEach void setup() {
        host=member();a=member();b=member();room=Room.create("waiting",3,false,null,1000);
        room.joinRoom(host);rooms.save(room);games.sitDown(room.getRoomId(),host,1);
    }
    @AfterEach void cleanup() { rooms.delete(room.getRoomId()); }
    void seat(String name,int index) { games.joinObserver(room.getRoomId(),name);games.sitDown(room.getRoomId(),name,index); }
    void ready(String name,boolean ready) { games.setReady(room.getRoomId(),name,ready); }
    void expire() {
        room.setWaitingDeadline(System.currentTimeMillis()-1);
        games.expireWaiting(room.getRoomId(),room,room.getWaitingDeadline(),room.getWaitingTarget());
    }
    @Test void creatorOwnsRoomAndOnlyHostCanStartAfterGuestsReady() {
        seat(a,2);assertEquals(host,room.getHost());
        assertThrows(IllegalArgumentException.class,()->games.setupGame(room.getRoomId(),a));
        assertThrows(IllegalArgumentException.class,()->games.setupGame(room.getRoomId(),host));
        ready(a,true);long deadline=room.getWaitingDeadline();assertTrue(deadline>System.currentTimeMillis());
        games.setupGame(room.getRoomId(),host);assertEquals(GameState.BETTING,room.getGame().getState());
        assertEquals(0,room.getWaitingDeadline());assertTrue(room.getReadyPlayers().isEmpty());
        games.expireWaiting(room.getRoomId(),room,deadline,host);assertTrue(room.contains(host));
    }
    @Test void soloHostStartsWithoutTimerAndObserverDoesNotBlock() {
        games.joinObserver(room.getRoomId(),a);assertEquals(0,room.getWaitingDeadline());
        games.setupGame(room.getRoomId(),host);assertEquals(1,room.getGame().getPlayers().size());
    }
    @Test void hostAndObserversCannotReadyAndUnseatedHostCannotStart() {
        games.joinObserver(room.getRoomId(),a);
        assertThrows(IllegalArgumentException.class,()->ready(host,true));
        assertThrows(IllegalArgumentException.class,()->ready(a,true));
        assertThrows(IllegalArgumentException.class,()->ready("outsider",true));
        room.getSeats().remove(1);room.getObservers().add(host);
        assertThrows(IllegalArgumentException.class,()->games.setupGame(room.getRoomId(),host));
    }
    @Test void soleGuestAndMultipleUnreadyGuestsDoNotTriggerKick() {
        seat(a,2);assertEquals(0,room.getWaitingDeadline());
        seat(b,3);assertEquals(0,room.getWaitingDeadline());
    }
    @Test void lastUnreadyGuestIsRemovedWithoutChangingWalletAndCannotImmediatelyReenter() {
        seat(a,2);seat(b,3);ready(a,true);
        assertEquals(b,room.getWaitingTarget());assertEquals("NOT_READY",room.getWaitingReason());
        expire();assertFalse(room.contains(b));assertEquals(host,room.getWaitingTarget());
        assertEquals(100000,members.findByUsername(b).orElseThrow().getMoney());
        assertThrows(IllegalArgumentException.class,()->roomService.joinRoom(room.getRoomId(),b,null));
    }
    @Test void idleHostIsRemovedAndLowestSeatInheritsHost() {
        seat(a,2);seat(b,3);ready(a,true);ready(b,true);expire();
        assertFalse(room.contains(host));assertEquals(a,room.getHost());assertFalse(room.getReadyPlayers().contains(a));
        assertTrue(room.getReadyPlayers().contains(b));games.setupGame(room.getRoomId(),a);
        assertEquals(GameState.BETTING,room.getGame().getState());
    }
    @Test void cancelReadyInvalidatesEarlierDeadlineAndDuplicateReadyDoesNotExtendIt() {
        seat(a,2);ready(a,true);long deadline=room.getWaitingDeadline();
        ready(a,true);assertEquals(deadline,room.getWaitingDeadline());
        ready(a,false);assertEquals(0,room.getWaitingDeadline());
        games.expireWaiting(room.getRoomId(),room,deadline,host);assertTrue(room.contains(host));
    }
    @Test void becomingReadyChangesTargetAndProtectsPreviouslyUnreadyGuest() {
        seat(a,2);seat(b,3);ready(a,true);long old=room.getWaitingDeadline();ready(b,true);
        games.expireWaiting(room.getRoomId(),room,old,b);assertTrue(room.contains(b));assertEquals(host,room.getWaitingTarget());
    }
    @Test void leavingHostTransfersAuthorityAndReadinessCannotChangeDuringPlay() {
        seat(a,2);ready(a,true);roomService.leaveRoom(room.getRoomId(),host);
        assertEquals(a,room.getHost());games.setupGame(room.getRoomId(),a);
        assertThrows(IllegalArgumentException.class,()->ready(a,false));
    }
    @Test void snapshotRestoresHostReadinessAndCooldown() {
        seat(a,2);seat(b,3);ready(a,true);expire();
        Room copy=codec.decode(codec.encode(room));
        assertEquals(room.getHost(),copy.getHost());assertEquals(room.getReadyPlayers(),copy.getReadyPlayers());
        assertEquals(room.getWaitingDeadline(),copy.getWaitingDeadline());assertEquals(room.getKickedUntil(),copy.getKickedUntil());
        rooms.load();room=rooms.findById(room.getRoomId());games.restoreWaitingTimers();
        assertEquals(host,room.getHost());assertTrue(room.getWaitingDeadline()>System.currentTimeMillis());
    }
    @Test void legacySnapshotWithoutReadinessFieldsGetsDeterministicHost() {
        String old="{\"schema\":1,\"id\":\"legacy\",\"title\":\"old\",\"maxPlayers\":2,\"privateRoom\":false,\"inviteHash\":null,\"minBet\":1000,\"seats\":{\"2\":\"guest\",\"1\":\"creator\"},\"observers\":[],\"paused\":false,\"game\":null}";
        Room restored=codec.decode(old);assertEquals("creator",restored.getHost());assertTrue(restored.getReadyPlayers().isEmpty());
    }
}
