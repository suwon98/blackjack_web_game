package com.project.blackjack.domain.room.entity;

import com.project.blackjack.domain.game.entity.BlackjackGame;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class Room {
    private String roomId;
    private String title;
    private int maxPlayers;
    private boolean isPrivate;
    private String inviteCode;
    private long minBet;

    private Map<Integer, String> seats;
    private Set<String> observers;

    private BlackjackGame game;

    public static Room create(String title, int maxPlayers, boolean isPrivate, String inviteCode, long minBet) {
        Room room = new Room();
        room.roomId = UUID.randomUUID().toString();
        room.title = title;
        room.maxPlayers = maxPlayers;
        room.isPrivate = isPrivate;
        room.inviteCode = inviteCode;
        room.minBet = minBet;
        room.seats = new ConcurrentHashMap<>();
        room.observers = ConcurrentHashMap.newKeySet();
        return room;
    }

    public void joinRoom(String username) {
        if (!seats.containsValue(username)) {
            observers.add(username);
        }
    }

    public boolean takeSeat(String username, int seatIndex) {
        if (seatIndex < 1 || seatIndex > maxPlayers) return false;
        if (seats.containsKey(seatIndex)) return false;

        seats.values().remove(username);
        seats.put(seatIndex, username);
        observers.remove(username);
        return true;
    }

    public void leaveRoom(String username) {
        seats.values().remove(username);
        observers.remove(username);
    }

    public int getTotalPlayerCount() {
        return seats.size() + observers.size();
    }
}