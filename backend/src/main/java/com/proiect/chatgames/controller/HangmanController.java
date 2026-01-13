package com.proiect.chatgames.controller;

import com.proiect.chatgames.service.GameService;
import com.proiect.chatgames.websocket.MainWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/hangman")
@CrossOrigin(origins = "*")
public class HangmanController {

    @Autowired
    GameService gameService;

    @Autowired
    MainWebSocketHandler wsHandler;

    @GetMapping("/games")
    public Map<String, Object> getGames() {
        return Map.of(
                "success", true,
                "games", gameService.getAllHangmanGames()
        );
    }

    @PostMapping("/create")
    public Map<String, Object> createGame(@RequestBody Map<String, String> payload) {
        String gameId = payload.get("gameId");
        String username = payload.get("username");

        gameService.createHangmanGame(gameId, username);

        // 1. Update lobby pentru toata lumea
        wsHandler.broadcastLobbyUpdate();

        // 2. Trimite starea initiala DOAR creatorului (Fix loading infinit)
        try {
            var game = gameService.getAllHangmanGames().stream()
                    .filter(g -> g.getGameId().equals(gameId)).findFirst().orElse(null);

            if (game != null) {
                wsHandler.sendToUser(username, Map.of(
                        "type", "hangman_game_state",
                        "gameState", game
                ));
            }
        } catch (Exception e) { e.printStackTrace(); }

        return Map.of("success", true);
    }

    @PostMapping("/join")
    public Map<String, Object> joinGame(@RequestBody Map<String, String> payload) {
        String gameId = payload.get("gameId");
        String username = payload.get("username");

        gameService.joinHangmanGame(gameId, username);

        // 1. Update lobby
        wsHandler.broadcastLobbyUpdate();

        // 2. Trimite starea celor implicati
        try {
            var game = gameService.getAllHangmanGames().stream()
                    .filter(g -> g.getGameId().equals(gameId)).findFirst().orElse(null);

            if (game != null) {
                // Trimitem celui care intra
                wsHandler.sendToUser(username, Map.of(
                        "type", "hangman_game_state",
                        "gameState", game
                ));
                // Trimitem si host-ului
                wsHandler.sendToUser(game.getHostUsername(), Map.of(
                        "type", "hangman_game_state",
                        "gameState", game
                ));
            }
        } catch (Exception e) { e.printStackTrace(); }

        return Map.of("success", true);
    }
}