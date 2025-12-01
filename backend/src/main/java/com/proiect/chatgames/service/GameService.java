package com.proiect.chatgames.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.proiect.chatgames.websocket.MainWebSocketHandler;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {
    // Aici tinem minte toate jocurile active (in memoria RAM)
    private final Map<String, HangmanGame> hangmanGames = new ConcurrentHashMap<>();

    // --- LOGICA HANGMAN ---
    // Aceasta metoda este apelata cand vine un mesaj de joc prin WebSocket
    public void handleHangmanMessage(String sender, JsonNode json, MainWebSocketHandler ws) throws IOException {
        String type = json.get("type").asText();
        String gameId = "";

        if (json.has("gameId")) {
            gameId = json.get("gameId").asText();
        } else {
            return;
        }

        HangmanGame game = hangmanGames.get(gameId);
        if (game == null) return;

        // Cazul 1: Gazda seteaza cuvantul
        if (type.equals("hangman_set_word") && sender.equals(game.hostUsername)) {
            String word = json.get("word").asText().trim().toUpperCase();
            if(!word.isEmpty()) {
                game.word = word;
                // Umplem cu underscore: "CAT" -> "___"
                game.maskedWord = "_".repeat(word.length());
                game.status = "in_progress";
                broadcastGameState(game, ws);
            }
        }
        // Cazul 2: Jucatorul ghiceste o litera
        else if (type.equals("hangman_guess_letter") && sender.equals(game.guesserUsername)) {
            String letter = json.get("letter").asText().toUpperCase();

            // Verificam daca litera e valida si nu a mai fost folosita
            if (!game.guessedLetters.contains(letter) && letter.length() == 1) {
                game.guessedLetters.add(letter);

                if (game.word.contains(letter)) {
                    // Litera e corecta! Actualizam masca
                    StringBuilder newMask = new StringBuilder();
                    for (int i = 0; i < game.word.length(); i++) {
                        char c = game.word.charAt(i);
                        if (game.guessedLetters.contains(String.valueOf(c))) {
                            newMask.append(c);
                        } else {
                            newMask.append('_');
                        }
                    }
                    game.maskedWord = newMask.toString();

                    // Verificam daca a castigat
                    if (!game.maskedWord.contains("_")) {
                        game.status = "won";
                    }
                } else {
                    // Litera e gresita
                    game.mistakes++;
                    if (game.mistakes >= 6) {
                        game.status = "lost";
                        game.maskedWord = game.word; // Aratam cuvantul la final
                    }
                }
                broadcastGameState(game, ws);
            }
        }
    }

    // Metode ajutatoare pentru creare si alaturare
    public void createHangmanGame(String gameId, String host) {
        HangmanGame g = new HangmanGame();
        g.gameId = gameId;
        g.hostUsername = host;
        hangmanGames.put(gameId, g);
    }

    public void joinHangmanGame(String gameId, String guesser) {
        HangmanGame g = hangmanGames.get(gameId);
        if (g != null && g.guesserUsername == null) {
            g.guesserUsername = guesser;
            g.status = "waiting_for_word";
        }
    }

    // Returneaza lista de jocuri pentru meniul principal
    public Collection<HangmanGame> getAllGames() {
        return hangmanGames.values();
    }

    // Trimite starea jocului catre ambii jucatori
    private void broadcastGameState(HangmanGame game, MainWebSocketHandler ws) throws IOException {
        Map<String, Object> msg = Map.of(
                "type", "hangman_game_state",
                "gameState", game
        );
        // Trimitem gazdei
        ws.sendToUser(game.hostUsername, msg);
        // Trimitem jucatorului (daca exista)
        if (game.guesserUsername != null) {
            ws.sendToUser(game.guesserUsername, msg);
        }
    }

    // Clasa interna care defineste un joc
    public static class HangmanGame {
        public String gameId;
        public String hostUsername;
        public String guesserUsername;
        public String word;
        public String maskedWord;
        public int mistakes = 0;
        public List<String> guessedLetters = new ArrayList<>();
        public String status = "waiting_for_guesser";
    }
}