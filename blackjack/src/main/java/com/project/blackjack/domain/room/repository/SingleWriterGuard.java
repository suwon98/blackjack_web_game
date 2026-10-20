package com.project.blackjack.domain.room.repository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import java.sql.Connection;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

/** The in-memory broker/cache supports exactly one writer per production database. */
@Component @RequiredArgsConstructor
public class SingleWriterGuard {
    private final DataSource source;
    private final Environment environment;
    private Connection lease;
    @PostConstruct public void acquire() throws Exception {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) return;
        lease=source.getConnection();
        try(var statement=lease.createStatement()){statement.execute("SET SESSION wait_timeout=31536000");}
        try(var statement=lease.createStatement();var result=statement.executeQuery("SELECT GET_LOCK('blackjack-single-writer',0)")) {
            if(!result.next() || result.getInt(1)!=1) throw new IllegalStateException("Another blackjack server owns this database");
        }
    }
    public synchronized void check() {
        if(lease==null) return;
        try(var statement=lease.createStatement();var result=statement.executeQuery("SELECT IS_USED_LOCK('blackjack-single-writer')=CONNECTION_ID()")) {
            if(!result.next() || !result.getBoolean(1)) throw new IllegalStateException("Database writer lease lost; restart required");
        } catch(java.sql.SQLException e) { throw new IllegalStateException("Database writer lease unavailable",e); }
    }
    @PreDestroy public void release() throws Exception { if(lease!=null) { try(var statement=lease.createStatement()){statement.execute("SELECT RELEASE_LOCK('blackjack-single-writer')");} finally {lease.close();} } }
}
