package com.project.blackjack.domain.room.repository;
import com.project.blackjack.domain.room.entity.Room;
import com.project.blackjack.domain.game.entity.GameState;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
@Component @RequiredArgsConstructor
public class RoomManager {
    private final RoomRecordRepository records;
    private final RoomCodec codec;
    private final SingleWriterGuard guard;
    private final Map<String,Room> rooms=new ConcurrentHashMap<>();
    @PostConstruct public void load() {
        for(RoomRecord record:records.findAll()) {
            Room r=codec.decode(record.getPayload());
            // Never spend an offline player's balance on startup. Members explicitly resume.
            if(r.getGame()!=null && r.getGame().getState()!=GameState.WAITING) r.setPaused(true);
            rooms.put(r.getRoomId(),r);
        }
    }
    public Room save(Room room) { persist(room);rooms.put(room.getRoomId(),room);return room; }
    public void persist(Room room) {
        guard.check();
        RoomRecord record=records.findById(room.getRoomId()).orElseGet(() -> new RoomRecord(room.getRoomId(),""));
        record.setPayload(codec.encode(room));records.saveAndFlush(record);
    }
    public Room findById(String id) { return id==null ? null : rooms.get(id); }
    public List<Room> findAll() { return new ArrayList<>(rooms.values()); }
    public void delete(String id) { records.deleteById(id);rooms.remove(id); }
}
