package com.project.blackjack.domain.room.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoomJoinRequest {
    private String username;
    private String inviteCode;
}
