package com.project.blackjack;

import com.project.blackjack.domain.member.entity.*;
import com.project.blackjack.domain.member.repository.MemberRepository;
import com.project.blackjack.domain.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RechargeTests {
    @Autowired MemberRepository members;
    @Autowired MemberService service;
    @Test void concurrentRechargeCreditsOnceAndPreservesBalance() throws Exception {
        String username = "charge" + UUID.randomUUID().toString().substring(0, 8);
        members.save(Member.builder().username(username).nickname(username).role(Role.USER).money(900L).build());
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        Callable<Boolean> charge = () -> { gate.await(); try { service.rechargeMoney(username); return true; } catch (IllegalArgumentException exception) { return false; } };
        try {
            var first = pool.submit(charge); var second = pool.submit(charge); gate.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(100900L, members.findByUsername(username).orElseThrow().getMoney());
            assertThrows(IllegalArgumentException.class, () -> service.rechargeMoney(username));
        } finally { pool.shutdownNow(); }
    }
}
