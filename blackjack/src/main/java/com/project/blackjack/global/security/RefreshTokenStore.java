package com.project.blackjack.global.security;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
@Component @RequiredArgsConstructor
public class RefreshTokenStore {
    private final RefreshSessionRepository sessions;
    private final JwtTokenProvider tokens;
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    @Transactional public void save(String user,String token) {
        RefreshSession s=sessions.locked(user).orElseGet(RefreshSession::new);
        s.setUsername(user);s.setTokenHash(hash(token));s.setSessionId(tokens.tokenId(token));s.setExpires(tokens.expires(token));sessions.saveAndFlush(s);
    }
    // Called inside the reissue transaction; row lock serializes refresh token rotation.
    @Transactional public boolean matches(String user,String token) {
        return sessions.locked(user).filter(s -> s.getExpires()>System.currentTimeMillis() && MessageDigest.isEqual(s.getTokenHash().getBytes(StandardCharsets.US_ASCII),hash(token).getBytes(StandardCharsets.US_ASCII))).isPresent();
    }
    @Transactional(readOnly=true) public boolean active(String user,String sid) {
        return sid!=null && sessions.findById(user).filter(s -> s.getExpires()>System.currentTimeMillis() && sid.equals(s.getSessionId())).isPresent();
    }
    @Transactional public void delete(String user) { sessions.deleteById(user); }
}
