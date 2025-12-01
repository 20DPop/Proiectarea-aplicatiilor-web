package com.proiect.chatgames.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proiect.chatgames.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MainWebSocketHandler extends TextWebSocketHandler {

    // Lista sesiunilor active (Socket -> Username)
    private static final Map<WebSocketSession, String> sessions = new ConcurrentHashMap<>();
    // Lista camerelor de chat (NumeCamera -> ListaUseri)
    private static final Map<String, Set<String>> chatRooms = new ConcurrentHashMap<>();

    private final ObjectMapper mapper = new ObjectMapper();

    // Folosim @Lazy pentru a evita dependenta circulara (Service <-> Handler)
    @Autowired
    @Lazy
    private GameService gameService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Extragem username din URL: ws://localhost:3000/ws?username=Alex
        String query = session.getUri().getQuery();
        String username = "Guest";

        if (query != null && query.contains("username=")) {
            // Un mic hack simplu pentru a lua valoarea parametrului
            try {
                username = query.split("username=")[1].split("&")[0];
            } catch (Exception e) {
                username = "Guest-" + session.getId();
            }
        }

        sessions.put(session, username);
        System.out.println("User conectat: " + username);

        // 1. Trimitem lista de useri online tuturor
        broadcastUserList();

        // 2. Trimitem lista de camere disponibile doar celui nou conectat
        sendJson(session, Map.of("type", "available_rooms", "content", chatRooms.keySet()));

        // 3. Trimitem actualizari lobby Hangman (daca exista)
        broadcastLobbyUpdate();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String username = sessions.remove(session);
        System.out.println("User deconectat: " + username);

        // Scoatem userul din toate camerele de chat
        chatRooms.forEach((room, users) -> {
            if (users.remove(username)) {
                try { broadcastRoomCount(room); } catch (Exception e) {}
            }
        });

        broadcastUserList();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        JsonNode json = mapper.readTree(payload);

        // Verificam sa avem un tip de mesaj
        if (!json.has("type")) return;

        String type = json.get("type").asText();
        String sender = sessions.get(session);

        switch (type) {
            // --- CHAT GLOBAL ---
            case "broadcast":
                if (json.has("content")) {
                    String content = json.get("content").asText();
                    broadcastAll(Map.of(
                            "type", "broadcast",
                            "username", sender,
                            "content", content
                    ));
                }
                break;

            // --- CHAT PRIVAT ---
            case "private_message":
                String toUser = json.get("to").asText();
                String text = json.get("text").asText();
                sendPrivateMessage(sender, toUser, text);
                break;

            // --- CAMERE (ROOMS) ---
            case "create_room":
            case "join_room":
                String room = json.get("room").asText();
                chatRooms.computeIfAbsent(room, k -> ConcurrentHashMap.newKeySet()).add(sender);

                // Confirmare catre user
                sendJson(session, Map.of("type", "joined_rooms", "content", List.of(room)));

                // Anuntam pe toata lumea ca exista o camera noua
                broadcastAll(Map.of("type", "available_rooms", "content", chatRooms.keySet()));
                broadcastRoomCount(room);
                break;

            case "leave_room":
                String roomToLeave = json.get("room").asText();
                if (chatRooms.containsKey(roomToLeave)) {
                    chatRooms.get(roomToLeave).remove(sender);
                    broadcastRoomCount(roomToLeave);
                    // Daca e goala, o stergem
                    if(chatRooms.get(roomToLeave).isEmpty()) {
                        chatRooms.remove(roomToLeave);
                        broadcastAll(Map.of("type", "available_rooms", "content", chatRooms.keySet()));
                    }
                }
                break;

            case "sendRoomMessage":
                String targetRoom = json.get("room").asText();
                String msgText = json.get("text").asText();
                broadcastToRoom(targetRoom, sender, msgText);
                break;

            // --- JOCURI ---
            // Aceste mesaje le delegam catre Service
            case "hangman_set_word":
            case "hangman_guess_letter":
                gameService.handleHangmanMessage(sender, json, this);
                break;
        }
    }

    // --- METODE AJUTATOARE ---

    private void broadcastAll(Object message) throws IOException {
        String jsonMsg = mapper.writeValueAsString(message);
        for (WebSocketSession s : sessions.keySet()) {
            if (s.isOpen()) s.sendMessage(new TextMessage(jsonMsg));
        }
    }

    private void broadcastUserList() throws IOException {
        broadcastAll(Map.of("type", "usernames", "content", new ArrayList<>(sessions.values())));
    }

    public void sendJson(WebSocketSession session, Object message) throws IOException {
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(message)));
        }
    }

    public void sendToUser(String username, Object message) throws IOException {
        for (Map.Entry<WebSocketSession, String> entry : sessions.entrySet()) {
            if (entry.getValue().equals(username)) {
                sendJson(entry.getKey(), message);
            }
        }
    }

    private void sendPrivateMessage(String sender, String recipient, String text) throws IOException {
        Map<String, Object> msg = Map.of(
                "type", "private_message",
                "sender", sender,
                "to", recipient,
                "text", text
        );
        sendToUser(recipient, msg);
        sendToUser(sender, msg); // Trimitem si expeditorului ca sa vada ce a scris
    }

    private void broadcastToRoom(String room, String sender, String text) throws IOException {
        Set<String> usersInRoom = chatRooms.get(room);
        if (usersInRoom != null) {
            Map<String, Object> msg = Map.of(
                    "type", "room_message",
                    "room", room,
                    "sender", sender,
                    "text", text
            );
            for (Map.Entry<WebSocketSession, String> entry : sessions.entrySet()) {
                if (usersInRoom.contains(entry.getValue())) {
                    sendJson(entry.getKey(), msg);
                }
            }
        }
    }

    private void broadcastRoomCount(String room) throws IOException {
        int count = chatRooms.containsKey(room) ? chatRooms.get(room).size() : 0;
        broadcastAll(Map.of("type", "room_user_count", "room", room, "count", count));
    }

    public void broadcastLobbyUpdate() {
        try {
            broadcastAll(Map.of(
                    "type", "hangman_lobby_update",
                    "games", gameService.getAllGames()
            ));
        } catch (IOException e) { e.printStackTrace(); }
    }
}