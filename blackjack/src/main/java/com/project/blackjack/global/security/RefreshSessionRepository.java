package com.project.blackjack.global.security;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface RefreshSessionRepository extends JpaRepository<RefreshSession,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s where s.username=:user")
    Optional<RefreshSession> locked(String user);
}
