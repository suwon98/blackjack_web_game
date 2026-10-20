package com.project.blackjack.global.security;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="refresh_session") @Getter @Setter @NoArgsConstructor
public class RefreshSession {
    @Id @Column(length=50) private String username;
    @Column(nullable=false,length=64) private String tokenHash;
    @Column(nullable=false,length=64) private String sessionId;
    private long expires;
    @Version private Long version;
}
