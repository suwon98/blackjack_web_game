package com.project.blackjack.domain.game.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ReceiptRepository extends JpaRepository<RoundReceipt,String> {
    List<RoundReceipt> findTop20ByUsernameOrderByCreatedAtDesc(String username);
}
