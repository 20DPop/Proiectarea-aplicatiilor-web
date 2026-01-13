package com.proiect.chatgames.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.proiect.chatgames.model.HangmanGame;
import com.proiect.chatgames.model.poker.EvaluatedHand;
import com.proiect.chatgames.model.poker.PokerGame;
import com.proiect.chatgames.model.poker.PokerPlayer;
import com.proiect.chatgames.websocket.MainWebSocketHandler;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    // Stocare jocuri in memorie (RAM)
    private final Map<String, HangmanGame> hangmanGames = new ConcurrentHashMap<>();
    private final Map<String, PokerGame> pokerGames = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // HANGMAN LOGIC
    // -------------------------------------------------------------------------

    public void handleHangmanMessage(String sender, JsonNode json, MainWebSocketHandler ws) throws IOException {
        if (!json.has("gameId")) return;
        String gameId = json.get("gameId").asText();
        HangmanGame game = hangmanGames.get(gameId);

        if (game == null) {
            // Optional: Auto-create daca nu exista (pentru robustete)
            return;
        }

        String type = json.get("type").asText();

        // 1. Host seteaza cuvantul
        if (type.equals("hangman_set_word") && sender.equals(game.getHostUsername())) {
            String word = json.get("word").asText();
            try {
                setHangmanWord(game, word);
                broadcastHangmanState(game, ws);
            } catch (IllegalArgumentException e) {
                System.err.println("Hangman Error: " + e.getMessage());
            }
        }

        // 2. Jucatorul ghiceste o litera
        else if (type.equals("hangman_guess_letter") && sender.equals(game.getGuesserUsername())) {
            String letter = json.get("letter").asText();
            try {
                guessHangmanLetter(game, letter);
                broadcastHangmanState(game, ws);
            } catch (IllegalArgumentException e) {
                System.err.println("Hangman Error: " + e.getMessage());
            }
        }
    }

    public void createHangmanGame(String gameId, String hostUsername) {
        HangmanGame game = new HangmanGame(gameId, hostUsername);
        game.setStatus("waiting_for_guesser");
        hangmanGames.put(gameId, game);
    }

    public void joinHangmanGame(String gameId, String guesserUsername) {
        HangmanGame game = hangmanGames.get(gameId);
        if (game != null && game.getStatus().equals("waiting_for_guesser")) {
            game.setGuesserUsername(guesserUsername);
            game.setStatus("waiting_for_word");
        }
    }

    private void setHangmanWord(HangmanGame game, String word) {
        if (!game.getStatus().equals("waiting_for_word")) {
            throw new IllegalArgumentException("Game is not waiting for word");
        }
        if (word == null || word.length() < 3) {
            throw new IllegalArgumentException("Word too short");
        }
        game.setSecretWord(word.toUpperCase());
        game.setStatus("in_progress");
        calculateHangmanMask(game);
    }

    private void guessHangmanLetter(HangmanGame game, String letter) {
        if (!game.getStatus().equals("in_progress")) return;

        String upper = letter.toUpperCase();
        if (game.getGuessedLetters().contains(upper)) return;

        game.getGuessedLetters().add(upper);

        if (!game.getSecretWord().contains(upper)) {
            game.setMistakes(game.getMistakes() + 1);
        }

        calculateHangmanMask(game);
        checkHangmanWin(game);
    }

    private void calculateHangmanMask(HangmanGame game) {
        if (game.getSecretWord() == null) return;
        StringBuilder sb = new StringBuilder();
        String word = game.getSecretWord();
        boolean isEnd = game.getStatus().equals("won") || game.getStatus().equals("lost");

        for (int i = 0; i < word.length(); i++) {
            String l = String.valueOf(word.charAt(i));
            if (game.getGuessedLetters().contains(l) || isEnd) {
                sb.append(l);
            } else {
                sb.append("_");
            }
            if (i < word.length() - 1) sb.append(" ");
        }
        game.setMaskedWord(sb.toString());
    }

    private void checkHangmanWin(HangmanGame game) {
        String word = game.getSecretWord();
        boolean allGuessed = true;
        for (char c : word.toCharArray()) {
            if (!game.getGuessedLetters().contains(String.valueOf(c))) {
                allGuessed = false;
                break;
            }
        }

        if (allGuessed) {
            game.setStatus("won");
            calculateHangmanMask(game);
        } else if (game.getMistakes() >= game.getMaxGuesses()) {
            game.setStatus("lost");
            calculateHangmanMask(game);
        }
    }

    public Collection<HangmanGame> getAllHangmanGames() {
        return hangmanGames.values();
    }

    private void broadcastHangmanState(HangmanGame game, MainWebSocketHandler ws) throws IOException {
        Map<String, Object> gameState = new HashMap<>();
        gameState.put("gameId", game.getGameId());
        gameState.put("hostUsername", game.getHostUsername());
        gameState.put("guesserUsername", game.getGuesserUsername());
        gameState.put("guessedLetters", game.getGuessedLetters());
        gameState.put("mistakes", game.getMistakes());
        gameState.put("maxGuesses", game.getMaxGuesses());
        gameState.put("maskedWord", game.getMaskedWord());
        gameState.put("status", game.getStatus());

        if (game.getStatus().equals("won") || game.getStatus().equals("lost")) {
            gameState.put("secretWord", game.getSecretWord());
        }

        Map<String, Object> msg = Map.of("type", "hangman_game_state", "gameState", gameState);
        ws.sendToUser(game.getHostUsername(), msg);
        if (game.getGuesserUsername() != null) {
            ws.sendToUser(game.getGuesserUsername(), msg);
        }
    }

    // -------------------------------------------------------------------------
    // POKER LOGIC
    // -------------------------------------------------------------------------

    public void handlePokerMessage(String sender, JsonNode json, MainWebSocketHandler ws) throws IOException {
        String type = json.get("type").asText();
        String gameId = json.has("gameId") ? json.get("gameId").asText() : "";

        PokerGame game = pokerGames.get(gameId);
        if (game == null) return;

        try {
            if (type.equals("poker_action")) {
                String action = json.get("action").asText();
                int amount = json.has("amount") ? json.get("amount").asInt() : 0;
                // In acest proiect, token-ul este chiar username-ul
                game.handlePlayerAction(sender, action, amount);
                broadcastPokerState(game, ws);
            }
            else if (type.equals("poker_start_game")) {
                System.out.println("DEBUG: Am primit comanda START de la: " + sender);
                System.out.println("DEBUG: Creatorul mesei este: " + game.getCreatorUsername());
                System.out.println("DEBUG: Numar jucatori: " + game.getPlayers().size());

                if (sender.equals(game.getCreatorUsername())) {
                    try {
                        game.startGame();
                        System.out.println("DEBUG: Jocul a pornit cu succes!");
                        broadcastPokerState(game, ws);
                    } catch(Exception e) {
                        System.out.println("DEBUG EROARE START: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("DEBUG: Sender-ul nu este creatorul!");
                }
            }
            else if (type.equals("poker_start_new_hand")) {
                if (sender.equals(game.getCreatorUsername())) {
                    game.startNewHand();
                    broadcastPokerState(game, ws);
                }
            }
            else if (type.equals("poker_leave_game")) {
                game.removePlayer(sender);
                broadcastPokerState(game, ws);
                if(game.getPlayers().isEmpty()) {
                    pokerGames.remove(gameId);
                    ws.broadcastAll(Map.of("type", "poker_lobby_update", "games", getAllPokerGames()));
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Poker Error: " + e.getMessage());
            // Optional: trimite mesaj de eroare doar userului
        }
    }

    public void createPokerGame(String gameId, String creator, String pass, int sb, int bb, int maxP, int stack) {
        if (pokerGames.containsKey(gameId)) throw new IllegalArgumentException("Game ID exists");
        PokerGame game = new PokerGame(gameId, creator, pass, sb, bb, maxP);
        game.addPlayer(creator, creator, stack);
        pokerGames.put(gameId, game);
    }

    public void joinPokerGame(String gameId, String user, String pass, int stack) {
        PokerGame game = pokerGames.get(gameId);
        if (game == null) throw new IllegalArgumentException("Game not found");

        if (game.getPassword() != null && !game.getPassword().isEmpty() && !game.getPassword().equals(pass)) {
            throw new IllegalArgumentException("Wrong password");
        }
        game.addPlayer(user, user, stack);
    }

    public Collection<PokerGame> getAllPokerGames() {
        return pokerGames.values();
    }

    private void broadcastPokerState(PokerGame game, MainWebSocketHandler ws) throws IOException {
        // Trimitem fiecarui jucator starea publica + cartile lui private
        for (PokerPlayer p : game.getPlayers()) {
            // Generam starea publica (ascunde cartile adversarilor)
            Map<String, Object> state = game.getPublicState(p.getToken());

            // Mesaj 1: Starea mesei
            ws.sendToUser(p.getUsername(), Map.of(
                    "type", "poker_game_state",
                    "gameState", state
            ));

            // Mesaj 2: Cartile proprii (doar pentru el)
            ws.sendToUser(p.getUsername(), Map.of(
                    "type", "poker_hand",
                    "hand", p.getHand()
            ));
        }
    }
}