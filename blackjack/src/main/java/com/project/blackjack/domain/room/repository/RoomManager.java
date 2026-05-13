package com.project.blackjack.domain.room.repository;

import com.project.blackjack.domain.room.entity.Room;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomManager {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    // 테스트용 방
    @PostConstruct
    public void initTestRoom() {
        Room testRoom = Room.create("테스트용 블랙잭 방", 6, false, null, 1000L);
        testRoom.setRoomId("room1");

        rooms.put(testRoom.getRoomId(), testRoom);
    }

    public Room save(Room room) {
        rooms.put(room.getRoomId(), room);
        return room;
    }

    public Room findById(String roomId) {
        return rooms.get(roomId);
    }

    public List<Room> findAll() {
        return new ArrayList<>(rooms.values());
    }

    public void delete(String roomId) {
        rooms.remove(roomId);
    }
}
