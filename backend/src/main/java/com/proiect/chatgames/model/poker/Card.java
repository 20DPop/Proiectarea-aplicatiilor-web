package com.proiect.chatgames.model.poker;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Card {
    private String rank; // "2", "3", ... "T", "J", "Q", "K", "A"
    private String suit; // "h", "d", "c", "s"

    // Returnează valoarea numerică pentru comparații
    public int getValue() {
        if (rank == null) return 0;
        switch (rank) {
            case "2": return 2;
            case "3": return 3;
            case "4": return 4;
            case "5": return 5;
            case "6": return 6;
            case "7": return 7;
            case "8": return 8;
            case "9": return 9;
            case "10": return 10;
            case "J": return 11;
            case "Q": return 12;
            case "K": return 13;
            case "A": return 14;
            default: return 0;
        }
    }

    @Override
    public String toString() {
        return rank + suit;
    }
}