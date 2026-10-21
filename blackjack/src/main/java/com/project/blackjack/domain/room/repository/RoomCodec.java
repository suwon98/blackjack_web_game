package com.project.blackjack.domain.room.repository;
import com.project.blackjack.domain.room.entity.Room;
import com.project.blackjack.domain.game.entity.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
@Component
public class RoomCodec {
    private final JsonMapper json = JsonMapper.builder().build();
    public record SavedCard(Suit suit, Denomination denomination) {}
    public record SavedHand(List<SavedCard> cards, HandStatus status, long bet, boolean split, String result, long payout) {}
    public record SavedPlayer(String name, long money, boolean insurance, List<SavedHand> hands) {}
    public record SavedGame(List<SavedCard> deck, SavedHand dealer, List<SavedPlayer> players, GameState state,
        int currentPlayer, long revision, long deadline, long round, boolean settled, Map<String,Long> bets, Map<String,Boolean> insurance) {}
    public record Snapshot(int schema, String id, String title, int maxPlayers, boolean privateRoom, String inviteHash,
        long minBet, Map<Integer,String> seats, Set<String> observers, boolean paused, SavedGame game, String host, Set<String> readyPlayers, Long waitingDeadline, String waitingTarget, String waitingReason, Map<String,Long> kickedUntil) {}
    private List<SavedCard> cards(List<Card> cards) { return cards.stream().map(c -> new SavedCard(c.getSuit(),c.getDenomination())).toList(); }
    private SavedHand hand(Hand h) { return new SavedHand(cards(h.getCards()),h.getStatus(),h.getBetAmount(),h.isSplit(),h.getResult(),h.getPayout()); }
    public String encode(Room r) {
        BlackjackGame g=r.getGame(); SavedGame saved=null;
        if(g!=null) saved=new SavedGame(cards(g.getDeck().getCards()),hand(g.getDealer().getHand()),g.getPlayers().stream()
            .map(p -> new SavedPlayer(p.getName(),p.getMoney(),p.isHasInsurance(),p.getHands().stream().map(this::hand).toList())).toList(),
            g.getState(),g.getCurrentPlayerIndex(),g.getRevision(),g.getDeadline(),g.getRoundId(),g.isSettled(),Map.copyOf(g.getRoundBets()),Map.copyOf(g.getInsuranceDecisions()));
        return json.writeValueAsString(new Snapshot(1,r.getRoomId(),r.getTitle(),r.getMaxPlayers(),r.isPrivate(),r.getInviteCode(),r.getMinBet(),Map.copyOf(r.getSeats()),Set.copyOf(r.getObservers()),r.isPaused(),saved,r.getHost(),Set.copyOf(r.getReadyPlayers()),r.getWaitingDeadline(),r.getWaitingTarget(),r.getWaitingReason(),Map.copyOf(r.getKickedUntil())));
    }
    private List<Card> restoreCards(List<SavedCard> cards) { return cards.stream().map(c -> new Card(c.suit(),c.denomination())).toList(); }
    private Hand restoreHand(SavedHand h) {
        Hand out=new Hand(h.bet());out.getCards().addAll(restoreCards(h.cards()));out.setStatus(h.status());out.setSplit(h.split());out.setResult(h.result());out.setPayout(h.payout());return out;
    }
    public Room decode(String payload) {
        Snapshot s=json.readValue(payload,Snapshot.class);
        if(s.schema()!=1) throw new IllegalStateException("Unsupported game snapshot schema: "+s.schema());
        Room r=Room.create(s.title(),s.maxPlayers(),s.privateRoom(),s.inviteHash(),s.minBet());r.setRoomId(s.id());r.getSeats().putAll(s.seats());r.getObservers().addAll(s.observers());r.setPaused(s.paused());
        r.setHost(s.host()); r.electHost();
        if(s.readyPlayers()!=null) r.getReadyPlayers().addAll(s.readyPlayers());
        r.setWaitingDeadline(s.waitingDeadline()==null?0:s.waitingDeadline());r.setWaitingTarget(s.waitingTarget());r.setWaitingReason(s.waitingReason());
        if(s.kickedUntil()!=null) r.getKickedUntil().putAll(s.kickedUntil());
        if(s.game()!=null) {
            SavedGame a=s.game(); Deck d=new Deck();d.getCards().clear();d.getCards().addAll(restoreCards(a.deck()));
            Dealer dealer=new Dealer();dealer.initializeHand();dealer.getHand().getCards().addAll(restoreCards(a.dealer().cards()));
            List<Player> players=new ArrayList<>();
            for(SavedPlayer x:a.players()) { Player p=new Player(x.name(),x.money());p.setHasInsurance(x.insurance());x.hands().forEach(h -> p.getHands().add(restoreHand(h)));players.add(p); }
            BlackjackGame g=new BlackjackGame(d,dealer,players);g.setState(a.state());g.setCurrentPlayerIndex(a.currentPlayer());g.setRevision(a.revision());g.setDeadline(a.deadline());g.setRoundId(a.round());g.setSettled(a.settled());g.getRoundBets().putAll(a.bets());g.getInsuranceDecisions().putAll(a.insurance());r.setGame(g);
        }
        return r;
    }
    public void restore(Room target,String payload) {
        Room r=decode(payload);target.setGame(r.getGame());target.setSeats(r.getSeats());target.setObservers(r.getObservers());target.setPaused(r.isPaused());
        target.setHost(r.getHost());target.setReadyPlayers(r.getReadyPlayers());target.setWaitingDeadline(r.getWaitingDeadline());
        target.setWaitingTarget(r.getWaitingTarget());target.setWaitingReason(r.getWaitingReason());target.setKickedUntil(r.getKickedUntil());
    }
}
