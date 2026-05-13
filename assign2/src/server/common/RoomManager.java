package server.common;

import common.models.Room;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * In-memory, thread-safe registry of chat rooms and their members.
 *
 * Rooms are created on first join and removed when the last member leaves,
 * except for the default rooms which are always present.
 *
 */
public class RoomManager {

    // Default rooms that always exist, even when empty.
    private static final List<String> DEFAULT_ROOMS = List.of("musica", "filmes");

    // roomName -> set of ClientHandlers currently in that room.
    private final Map<String, Set<ClientHandler>> rooms = new HashMap<>();

    public RoomManager() {
        for (String name : DEFAULT_ROOMS) {
            rooms.put(name, new HashSet<ClientHandler>());
        }
        System.out.println("[RoomManager] Initialised with default rooms: " + DEFAULT_ROOMS);
    }

    private Set<ClientHandler> getOrCreateRoom(String roomName) {
        // Fast path for existing rooms, to avoid locking on every message.
        if (rooms.containsKey(roomName)){
            return rooms.get(roomName);
        }

        // Lock read and write access to the rooms map.
        synchronized (rooms) {
            // Check again in case another thread created the room while we were waiting for the lock.
            return rooms.computeIfAbsent(roomName, k -> new HashSet<>());
        }
    }

    /**
     * Adds a client to a room, creating the room if it doesn't exist.
     *
     * @return the Room the client joined.
     */
    public Room joinRoom(String roomName, ClientHandler client) {
        String key = normalise(roomName);
        // Create a lock for the room if it doesn't exist, then add the client to the room's member set.
        Set<ClientHandler> members = getOrCreateRoom(roomName);
        synchronized (members) {
            members.add(client);
        }
        System.out.println("[RoomManager] " + client + " joined room: " + key);
        return new Room(key);
    }

    /**
     * Removes a client from a room.
     * If the room becomes empty and is not a default room, it is deleted.
     */
    public void leaveRoom(String roomName, ClientHandler client) {
        String key = normalise(roomName);
        Set<ClientHandler> members = rooms.get(key);
        if (members == null) return;

        synchronized (members) {
            members.remove(client);
        }
        System.out.println("[RoomManager] " + client + " left room: " + key);

        // Clean up non-default empty rooms.
        if (members.isEmpty() && !DEFAULT_ROOMS.contains(key)) {
            synchronized (rooms) {
                if (members.isEmpty())
                    rooms.remove(key, members);   // only removes if still empty (avoids race)
            }
            System.out.println("[RoomManager] Room deleted (empty): " + key);
        }
    }

    /**
     * Removes a client from every room they are in (e.g. on disconnect).
     */
    public void leaveAllRooms(ClientHandler client) {
        synchronized (rooms) {
            rooms.forEach((name, members) -> {
                if (members.remove(client)) {
                    System.out.println("[RoomManager] " + client + " removed from room: " + name);
                    if (members.isEmpty() && !DEFAULT_ROOMS.contains(name)) {
                        rooms.remove(name, members);
                    }
                }
            });
        }
    }

    /**
     * @return an unmodifiable snapshot of all currently existing rooms.
     */
    public List<Room> listRooms() {
        return rooms.keySet().stream()
                .sorted()
                .map(Room::new)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * @return an unmodifiable snapshot of the members in a given room,
     *         or an empty set if the room does not exist.
     */
    public Set<ClientHandler> getMembers(String roomName) {
        Set<ClientHandler> members = rooms.get(normalise(roomName));
        if (members == null) return Collections.emptySet();
        return Collections.unmodifiableSet(members);
    }

    /**
     * @return true if the room exists (has been created).
     */
    public boolean roomExists(String roomName) {
        return rooms.containsKey(normalise(roomName));
    }

    /**
     * @return the number of members in a room, or 0 if it doesn't exist.
     */
    public int getMemberCount(String roomName) {
        Set<ClientHandler> members = rooms.get(normalise(roomName));
        return members == null ? 0 : members.size();
    }

    private static String normalise(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Room name cannot be null or blank");
        return name.trim().toLowerCase();
    }
}