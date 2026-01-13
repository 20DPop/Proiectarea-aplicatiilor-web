package com.proiect.chatgames.model.poker;

import java.util.*;
import java.util.stream.Collectors;

public class PokerHandEvaluator {

    private static final Map<String, Integer> RANK_VALUES = new HashMap<>();
    static {
        RANK_VALUES.put("2", 2); RANK_VALUES.put("3", 3); RANK_VALUES.put("4", 4);
        RANK_VALUES.put("5", 5); RANK_VALUES.put("6", 6); RANK_VALUES.put("7", 7);
        RANK_VALUES.put("8", 8); RANK_VALUES.put("9", 9); RANK_VALUES.put("T", 10);
        RANK_VALUES.put("J", 11); RANK_VALUES.put("Q", 12); RANK_VALUES.put("K", 13);
        RANK_VALUES.put("A", 14);
    }

    public static EvaluatedHand evaluateHand(List<Card> sevenCards) {
        List<List<Card>> combinations = new ArrayList<>();
        generateCombinations(sevenCards, 0, new ArrayList<>(), combinations);

        EvaluatedHand bestHand = new EvaluatedHand(-1, "No Hand", new ArrayList<>());

        for (List<Card> combo : combinations) {
            // Sortam descrescator dupa valoare
            combo.sort((a, b) -> RANK_VALUES.get(b.getRank()) - RANK_VALUES.get(a.getRank()));

            List<Integer> rankValues = combo.stream().map(c -> RANK_VALUES.get(c.getRank())).collect(Collectors.toList());
            boolean isFlush = combo.stream().allMatch(c -> c.getSuit().equals(combo.get(0).getSuit()));

            // Numaram frecventa rangurilor
            Map<Integer, Integer> rankCounts = new HashMap<>();
            for (Integer r : rankValues) rankCounts.put(r, rankCounts.getOrDefault(r, 0) + 1);

            List<Integer> counts = new ArrayList<>(rankCounts.values());
            counts.sort((a, b) -> b - a); // Sortam frecventele descrescator (ex: 3, 2 pentru Full House)

            // Verificare Chinta (Straight)
            List<Integer> uniqueSorted = new ArrayList<>(new HashSet<>(rankValues));
            Collections.sort(uniqueSorted);
            boolean isStraight = false;
            if (uniqueSorted.size() >= 5) {
                for (int i = 0; i <= uniqueSorted.size() - 5; i++) {
                    if (uniqueSorted.get(i + 4) - uniqueSorted.get(i) == 4) {
                        isStraight = true;
                        break;
                    }
                }
                // Chinta A-2-3-4-5 (Ace low)
                if (!isStraight && uniqueSorted.containsAll(Arrays.asList(14, 2, 3, 4, 5))) {
                    isStraight = true;
                }
            }

            String handName;
            int handRank;

            if (isFlush && isStraight) { handName = "Straight Flush"; handRank = 8; }
            else if (counts.get(0) == 4) { handName = "Four of a Kind"; handRank = 7; }
            else if (counts.get(0) == 3 && counts.size() > 1 && counts.get(1) == 2) { handName = "Full House"; handRank = 6; }
            else if (isFlush) { handName = "Flush"; handRank = 5; }
            else if (isStraight) { handName = "Straight"; handRank = 4; }
            else if (counts.get(0) == 3) { handName = "Three of a Kind"; handRank = 3; }
            else if (counts.get(0) == 2 && counts.size() > 1 && counts.get(1) == 2) { handName = "Two Pair"; handRank = 2; }
            else if (counts.get(0) == 2) { handName = "One Pair"; handRank = 1; }
            else { handName = "High Card"; handRank = 0; }

            // Comparatia cu cea mai buna mana gasita pana acum
            if (handRank > bestHand.getRank()) {
                bestHand = new EvaluatedHand(handRank, handName, rankValues);
            } else if (handRank == bestHand.getRank()) {
                for (int i = 0; i < rankValues.size(); i++) {
                    if (rankValues.get(i) > bestHand.getHighCardValues().get(i)) {
                        bestHand = new EvaluatedHand(handRank, handName, rankValues);
                        break;
                    }
                    if (rankValues.get(i) < bestHand.getHighCardValues().get(i)) break;
                }
            }
        }
        return bestHand;
    }

    private static void generateCombinations(List<Card> cards, int start, List<Card> current, List<List<Card>> result) {
        if (current.size() == 5) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < cards.size(); i++) {
            current.add(cards.get(i));
            generateCombinations(cards, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}