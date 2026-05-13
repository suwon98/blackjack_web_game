package com.project.blackjack.domain.room.dto;

import com.project.blackjack.domain.room.entity.Room;
import lombok.Getter;

@Getter
public class RoomListResponse {
    private String roomId;
    private String title;
    private int maxPlayers;
    private int currentPlayers;
    private boolean isPrivate;
    private long minBet;
    private boolean isPlaying;

    public RoomListResponse(Room room) {
        this.roomId = room.getRoomId();
        this.title = room.getTitle();
        this.maxPlayers = room.getMaxPlayers();
        this.currentPlayers = room.getTotalPlayerCount();
        this.isPrivate = room.isPrivate();
        this.minBet = room.getMinBet();
        this.isPlaying = room.getGame() != null;
    }
}
