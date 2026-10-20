package com.project.blackjack.domain.room.dto;

import com.project.blackjack.domain.room.entity.Room;
import lombok.Getter;

@Getter
public class RoomResponse {
    private String roomId;
    private String title;
    private int maxPlayers;
    private int currentPlayers;
    private boolean isPrivate;
    private long minBet;
    private boolean paused;
    @lombok.Setter private boolean joined;
    private boolean playing;
    @com.fasterxml.jackson.annotation.JsonProperty("isPrivate")
    public boolean isPrivate() { return isPrivate; }
    @com.fasterxml.jackson.annotation.JsonProperty("isPlaying")
    public boolean isPlaying() { return playing; }

    public RoomResponse(Room room) {
        this.roomId = room.getRoomId();
        this.title = room.getTitle();
        this.maxPlayers = room.getMaxPlayers();
        this.currentPlayers = room.getTotalPlayerCount();
        this.isPrivate = room.isPrivate();
        this.minBet = room.getMinBet();
        this.paused = room.isPaused();
        this.playing = room.getGame() != null && room.getGame().getState() != com.project.blackjack.domain.game.entity.GameState.WAITING;
    }
}
