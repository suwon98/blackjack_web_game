package com.project.blackjack.domain.room.repository;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="room_snapshot") @Getter @Setter @NoArgsConstructor
public class RoomRecord {
    @Id @Column(length=64) private String id;
    @Lob @Column(nullable=false, columnDefinition="LONGTEXT") private String payload;
    @Version private Long version;
    public RoomRecord(String id, String payload) { this.id=id; this.payload=payload; }
}
