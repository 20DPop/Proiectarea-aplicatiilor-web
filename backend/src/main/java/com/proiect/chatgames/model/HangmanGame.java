package com.proiect.chatgames.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class HangmanGame {
    private String gameId;
    private String hostUsername;
    private String guesserUsername;

    // Date secrete (nu le trimitem la frontend decat la final)
    private String secretWord;

    // Starea curenta
    private List<String> guessedLetters = new ArrayList<>();
    private int mistakes = 0;
    private final int maxGuesses = 6;

    // Statusuri posibile: "waiting_for_guesser", "waiting_for_word", "in_progress", "won", "lost"
    private String status = "waiting_for_guesser";

    // Aceasta e proprietatea calculata pe care o vede frontend-ul (ex: "_ A _ _")
    private String maskedWord;

    public HangmanGame(String gameId, String hostUsername) {
        this.gameId = gameId;
        this.hostUsername = hostUsername;
    }
}