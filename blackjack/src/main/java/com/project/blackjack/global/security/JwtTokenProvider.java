package com.project.blackjack.global.security;

import com.project.blackjack.domain.member.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(String username, Role role) { return createAccessToken(username,role,java.util.UUID.randomUUID().toString()); }
    public String createAccessToken(String username, Role role, String sessionId) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + accessExpiration);

        return Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .claim("type", "access")
                .claim("sid", sessionId)
                .issuedAt(now)
                .expiration(validity)
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(String username) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .subject(username)
                .claim("type", "refresh")
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(validity)
                .signWith(key)
                .compact();
    }

    private Claims claims(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
    public String tokenId(String token) { return claims(token).getId(); }
    public long expires(String token) { return claims(token).getExpiration().getTime(); }
    public String sessionId(String token) { return claims(token).get("sid",String.class); }

    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String username = claims.getSubject();
        String role = claims.get("role", String.class);
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role != null ? role : "ROLE_USER");

        return new UsernamePasswordAuthenticationToken(username, "", Collections.singleton(authority));
    }

    public boolean validateAccessToken(String token) { return validateType(token, "access"); }
    public boolean validateRefreshToken(String token) { return validateType(token, "refresh"); }
    private boolean validateType(String token, String type) {
        try { return type.equals(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().get("type", String.class)); }
        catch (Exception e) { return false; }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}