package com.proiect.chatgames.model.poker;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvaluatedHand {
    /**
     * Rangul numeric al mâinii (pentru comparație ușoară):
     * 8 = Straight Flush
     * 7 = Four of a Kind
     * 6 = Full House
     * 5 = Flush
     * 4 = Straight
     * 3 = Three of a Kind
     * 2 = Two Pair
     * 1 = One Pair
     * 0 = High Card
     */
    private int rank;

    // Numele lisibil al mâinii (ex: "Full House")
    private String name;

    /**
     * Lista valorilor cărților relevante, sortate descrescător.
     * Se folosește pentru departajare (Kicker).
     * Ex: Dacă doi jucători au o pereche, câștigă cel cu perechea mai mare.
     * Dacă perechile sunt egale, câștigă cel cu următoarea carte mai mare.
     */
    private List<Integer> highCardValues;
}