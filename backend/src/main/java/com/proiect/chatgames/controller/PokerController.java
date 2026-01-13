package com.proiect.chatgames.controller;

import com.proiect.chatgames.service.GameService;
import com.proiect.chatgames.websocket.MainWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/poker")
@CrossOrigin(origins = "*")
public class PokerController {

    @Autowired GameService gameService;
    @Autowired MainWebSocketHandler wsHandler;

    @GetMapping("/games")
    public Map<String, Object> getGames() {
        return Map.of("success", true, "games", gameService.getAllPokerGames());
    }

    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody Map<String, Object> payload) {
        try {
            String gameId = (String) payload.get("gameId");
            String creator = (String) payload.get("username");
            String pass = (String) payload.get("password");

            int sb = getInt(payload, "smallBlind", 10);
            int bb = getInt(payload, "bigBlind", 20);
            int maxP = getInt(payload, "maxPlayers", 9);
            int stack = getInt(payload, "stack", 1000);

            gameService.createPokerGame(gameId, creator, pass, sb, bb, maxP, stack);
            broadcastUpdate();

            // Trimitem starea initiala creatorului ca sa poata vedea masa
            var game = gameService.getAllPokerGames().stream()
                    .filter(g -> g.getGameId().equals(gameId)).findFirst().orElse(null);

            if(game != null) {
                wsHandler.sendToUser(creator, Map.of(
                        "type", "poker_game_state",
                        "gameState", game.getPublicState(creator)
                ));
            }

            return Map.of("success", true);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @PostMapping("/join")
    public Map<String, Object> join(@RequestBody Map<String, Object> payload) {
        try {
            String gameId = (String) payload.get("gameId");
            String user = (String) payload.get("username");
            String pass = (String) payload.get("password");
            int stack = getInt(payload, "stack", 1000);

            gameService.joinPokerGame(gameId, user, pass, stack);
            broadcastUpdate();

            // Trimitem starea actualizata tuturor jucatorilor de la masa
            var game = gameService.getAllPokerGames().stream()
                    .filter(g -> g.getGameId().equals(gameId)).findFirst().orElse(null);

            if(game != null) {
                for(var p : game.getPlayers()) {
                    wsHandler.sendToUser(p.getUsername(), Map.of(
                            "type", "poker_game_state",
                            "gameState", game.getPublicState(p.getToken())
                    ));
                }
            }

            return Map.of("success", true);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    // Helper pentru a citi numere din JSON in siguranta
    private int getInt(Map<String, Object> payload, String key, int defaultValue) {
        Object val = payload.get(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void broadcastUpdate() {
        try {
            wsHandler.broadcastAll(Map.of(
                    "type", "poker_lobby_update",
                    "games", gameService.getAllPokerGames()
            ));
        } catch (Exception e) {
            System.err.println("Broadcast error: " + e.getMessage());
        }
    }
}