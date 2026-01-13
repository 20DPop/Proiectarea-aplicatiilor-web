package com.proiect.chatgames.model.poker;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class PokerPlayer {
    private String username;
    private int stack;
    private List<Card> hand = new ArrayList<>();
    private int currentBet = 0;

    // Statusuri posibile: "active", "folded", "all-in", "out", "waiting"
    private String status = "active";

    private String token; // Identificator unic (poate fi username-ul)
    private boolean isWinner = false;
    private boolean hasActed = false;
    private EvaluatedHand evaluatedHand;

    public PokerPlayer(String username, int stack) {
        this.username = username;
        this.stack = stack;
    }

    public void resetForNewHand() {
        this.hand.clear();
        this.currentBet = 0;
        this.isWinner = false;
        this.hasActed = false;
        this.evaluatedHand = null;
        if (this.stack > 0) {
            this.status = "active";
        } else {
            this.status = "out";
        }
    }
}