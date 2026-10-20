package com.project.blackjack.domain.game.repository;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="round_receipt") @Getter @NoArgsConstructor
public class RoundReceipt {
    @Id @Column(length=160) private String id;
    @Column(nullable=false,length=50) private String username;
    @Column(nullable=false,length=64) private String roomId;
    private long roundNumber;
    private long stake;
    private long payout;
    private long balance;
    private long createdAt;
    public RoundReceipt(String room,long round,String user,long stake,long payout,long balance) {
        id=room+":"+round+":"+user;username=user;roomId=room;roundNumber=round;this.stake=stake;this.payout=payout;this.balance=balance;createdAt=System.currentTimeMillis();
    }
}
