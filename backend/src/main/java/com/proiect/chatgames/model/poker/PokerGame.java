package com.proiect.chatgames.model.poker;

import lombok.Data;
import java.util.*;
import java.util.stream.Collectors;

@Data
public class PokerGame {
    private String gameId;
    private String creatorUsername;
    private String password;
    private Map<String, PokerPlayer> playersByToken = new HashMap<>();
    private List<PokerPlayer> players = new ArrayList<>();

    // Optiuni
    private int smallBlind = 10;
    private int bigBlind = 20;
    private int minPlayers = 2;
    private int maxPlayers = 9;

    // Stare Joc
    private List<Card> deck = new ArrayList<>();
    private List<Card> board = new ArrayList<>();
    private int pot = 0;
    private boolean inProgress = false;
    private String round = "pre-game";
    private int dealerIndex = -1;
    private int currentPlayerIndex = -1;
    private PokerPlayer lastRaiser = null;

    private static final String[] SUITS = {"h", "d", "c", "s"};
    private static final String[] RANKS = {"2", "3", "4", "5", "6", "7", "8", "9", "T", "J", "Q", "K", "A"};

    public PokerGame(String gameId, String creatorUsername, String password, int smallBlind, int bigBlind, int maxPlayers) {
        this.gameId = gameId;
        this.creatorUsername = creatorUsername;
        this.password = password;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.maxPlayers = maxPlayers;
    }

    public void addPlayer(String token, String username, int stack) {
        if (playersByToken.containsKey(token)) throw new IllegalArgumentException("Esti deja la masa.");
        if (players.size() >= maxPlayers) throw new IllegalArgumentException("Masa este plina.");

        PokerPlayer player = new PokerPlayer(username, stack);
        player.setToken(token);
        players.add(player);
        playersByToken.put(token, player);
    }

    public void removePlayer(String token) {
        PokerPlayer p = playersByToken.get(token);
        if (p == null) return;

        // Daca era randul lui, dam fold automat
        if (inProgress && !p.getStatus().equals("folded") &&
                currentPlayerIndex >= 0 && currentPlayerIndex < players.size() &&
                players.get(currentPlayerIndex).getToken().equals(token)) {
            handlePlayerAction(token, "fold", 0);
        } else {
            p.setStatus("folded");
        }

        players.remove(p);
        playersByToken.remove(token);

        if (inProgress) {
            long activeCount = players.stream().filter(pl -> !pl.getStatus().equals("folded") && !pl.getStatus().equals("out")).count();
            if (activeCount <= 1) determineWinners();
        }
    }

    public void startGame() {
        if (players.size() < minPlayers) throw new IllegalArgumentException("Nu sunt suficienti jucatori.");
        if (inProgress) throw new IllegalArgumentException("Jocul este deja pornit.");
        inProgress = true;
        setupNewHand();
    }

    public void startNewHand() {
        if (!round.equals("showdown") && !round.equals("pre-game")) {
            // throw new IllegalArgumentException("Mana curenta nu s-a terminat.");
            // Pentru robustete, permitem restart daca e blocat, dar ideal aruncam eroare
        }
        inProgress = true;
        setupNewHand();
    }

    private void setupNewHand() {
        pot = 0;
        board.clear();
        players.forEach(PokerPlayer::resetForNewHand);

        // Eliminam jucatorii fara bani
        players = players.stream().filter(p -> !p.getStatus().equals("out")).collect(Collectors.toList());
        if (players.size() < minPlayers) {
            inProgress = false;
            return;
        }

        // Generam pachetul
        deck.clear();
        for (String s : SUITS) {
            for (String r : RANKS) {
                deck.add(new Card(r, s));
            }
        }
        Collections.shuffle(deck);

        // Blinds
        dealerIndex = (dealerIndex + 1) % players.size();
        int sbIndex = (dealerIndex + 1) % players.size();
        int bbIndex = (dealerIndex + 2) % players.size();

        postBlind(players.get(sbIndex), smallBlind);
        postBlind(players.get(bbIndex), bigBlind);

        // Impartim carti (2 de fiecare)
        for (int i = 0; i < 2; i++) {
            for (PokerPlayer p : players) {
                if (!p.getStatus().equals("out")) p.getHand().add(deck.remove(deck.size() - 1));
            }
        }

        currentPlayerIndex = (bbIndex + 1) % players.size();
        ensureActivePlayer();

        lastRaiser = players.get(bbIndex);
        round = "pre-flop";
    }

    private void postBlind(PokerPlayer p, int amount) {
        int blind = Math.min(p.getStack(), amount);
        p.setStack(p.getStack() - blind);
        p.setCurrentBet(blind);
    }

    public void handlePlayerAction(String token, String action, int amount) {
        if (round.equals("showdown")) return;

        PokerPlayer player = playersByToken.get(token);
        if (player == null || !players.get(currentPlayerIndex).getToken().equals(token)) {
            throw new IllegalArgumentException("Nu este randul tau.");
        }

        int highestBet = players.stream().mapToInt(PokerPlayer::getCurrentBet).max().orElse(0);

        switch (action.toLowerCase()) {
            case "fold":
                player.setStatus("folded");
                break;
            case "check":
                if (player.getCurrentBet() < highestBet) throw new IllegalArgumentException("Nu poti da check.");
                break;
            case "call":
                int callAmt = highestBet - player.getCurrentBet();
                if (callAmt >= player.getStack()) {
                    player.setCurrentBet(player.getCurrentBet() + player.getStack());
                    player.setStack(0);
                    player.setStatus("all-in");
                } else {
                    player.setStack(player.getStack() - callAmt);
                    player.setCurrentBet(player.getCurrentBet() + callAmt);
                }
                break;
            case "raise":
                int totalBet = amount;
                int raiseAmt = totalBet - player.getCurrentBet();
                if (totalBet < highestBet + bigBlind) throw new IllegalArgumentException("Raise prea mic.");
                if (raiseAmt > player.getStack()) throw new IllegalArgumentException("Fonduri insuficiente.");

                player.setStack(player.getStack() - raiseAmt);
                player.setCurrentBet(totalBet);
                lastRaiser = player;
                if (player.getStack() == 0) player.setStatus("all-in");
                break;
        }

        player.setHasActed(true);

        if (isRoundComplete()) {
            advanceToNextState();
        } else {
            moveToNextPlayer();
        }
    }

    private void advanceToNextState() {
        // Colectam banii in pot
        players.forEach(p -> {
            pot += p.getCurrentBet();
            p.setCurrentBet(0);
            if (p.getStatus().equals("active")) p.setHasActed(false);
        });

        long activeCount = players.stream().filter(p -> !p.getStatus().equals("folded") && !p.getStatus().equals("out")).count();
        if (activeCount <= 1) {
            determineWinners();
            return;
        }

        switch (round) {
            case "pre-flop":
                round = "flop";
                deck.remove(deck.size() - 1); // Burn
                board.add(deck.remove(deck.size() - 1));
                board.add(deck.remove(deck.size() - 1));
                board.add(deck.remove(deck.size() - 1));
                break;
            case "flop":
                round = "turn";
                deck.remove(deck.size() - 1);
                board.add(deck.remove(deck.size() - 1));
                break;
            case "turn":
                round = "river";
                deck.remove(deck.size() - 1);
                board.add(deck.remove(deck.size() - 1));
                break;
            case "river":
                determineWinners();
                return;
        }

        currentPlayerIndex = (dealerIndex + 1) % players.size();
        ensureActivePlayer();
        lastRaiser = null;
    }

    private void determineWinners() {
        // Colectam ultimele pariuri
        players.forEach(p -> {
            pot += p.getCurrentBet();
            p.setCurrentBet(0);
        });

        List<PokerPlayer> contenders = players.stream()
                .filter(p -> !p.getStatus().equals("folded") && !p.getStatus().equals("out"))
                .collect(Collectors.toList());

        if (contenders.size() == 1) {
            contenders.get(0).setStack(contenders.get(0).getStack() + pot);
            contenders.get(0).setWinner(true);
            contenders.get(0).setEvaluatedHand(new EvaluatedHand(0, "Castigator prin abandon", null));
        } else {
            List<PokerPlayer> winners = new ArrayList<>();
            EvaluatedHand bestHand = new EvaluatedHand(-1, "", new ArrayList<>());

            for (PokerPlayer p : contenders) {
                List<Card> allCards = new ArrayList<>(p.getHand());
                allCards.addAll(board);
                EvaluatedHand eval = PokerHandEvaluator.evaluateHand(allCards);
                p.setEvaluatedHand(eval);

                if (eval.getRank() > bestHand.getRank()) {
                    bestHand = eval;
                    winners.clear();
                    winners.add(p);
                } else if (eval.getRank() == bestHand.getRank()) {
                    // Tie-breaker
                    boolean isNewBest = false;
                    boolean isTie = true;
                    for (int i = 0; i < eval.getHighCardValues().size(); i++) {
                        if (eval.getHighCardValues().get(i) > bestHand.getHighCardValues().get(i)) {
                            isNewBest = true; isTie = false; break;
                        }
                        if (eval.getHighCardValues().get(i) < bestHand.getHighCardValues().get(i)) {
                            isTie = false; break;
                        }
                    }
                    if (isNewBest) {
                        bestHand = eval;
                        winners.clear();
                        winners.add(p);
                    } else if (isTie) {
                        winners.add(p);
                    }
                }
            }

            if (!winners.isEmpty()) {
                int share = pot / winners.size();
                winners.forEach(w -> {
                    w.setWinner(true);
                    w.setStack(w.getStack() + share);
                });
            }
        }
        round = "showdown";
        currentPlayerIndex = -1;
    }

    private void moveToNextPlayer() {
        int attempts = 0;
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            attempts++;
            if (attempts > players.size() * 2) {
                determineWinners(); // Fail-safe
                return;
            }
        } while (!players.get(currentPlayerIndex).getStatus().equals("active"));
    }

    private void ensureActivePlayer() {
        if (currentPlayerIndex >= 0 && currentPlayerIndex < players.size()) {
            while(!players.get(currentPlayerIndex).getStatus().equals("active")) {
                currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            }
        }
    }

    private boolean isRoundComplete() {
        List<PokerPlayer> active = players.stream().filter(p -> p.getStatus().equals("active")).collect(Collectors.toList());
        if (active.isEmpty()) return true;

        int highestBet = players.stream()
                .filter(p -> !p.getStatus().equals("folded") && !p.getStatus().equals("out"))
                .mapToInt(PokerPlayer::getCurrentBet).max().orElse(0);

        return active.stream().allMatch(p -> p.isHasActed() && p.getCurrentBet() == highestBet);
    }

    // Metoda pentru a genera JSON-ul pentru frontend
    public Map<String, Object> getPublicState(String userToken) {
        Map<String, Object> state = new HashMap<>();
        state.put("gameId", gameId);
        state.put("creatorUsername", creatorUsername);
        state.put("inProgress", inProgress);
        state.put("round", round);
        state.put("pot", pot + players.stream().mapToInt(PokerPlayer::getCurrentBet).sum());
        state.put("board", board);
        state.put("maxPlayers", maxPlayers);
        state.put("minPlayers", minPlayers);
        state.put("currentPlayerToken", currentPlayerIndex != -1 ? players.get(currentPlayerIndex).getToken() : null);

        // Optiuni
        Map<String, Integer> opts = new HashMap<>();
        opts.put("smallBlind", smallBlind);
        opts.put("bigBlind", bigBlind);
        state.put("options", opts);

        List<Map<String, Object>> publicPlayers = new ArrayList<>();
        for (PokerPlayer p : players) {
            Map<String, Object> pMap = new HashMap<>();
            pMap.put("username", p.getUsername());
            pMap.put("stack", p.getStack());
            pMap.put("currentBet", p.getCurrentBet());
            pMap.put("status", p.getStatus());
            pMap.put("isWinner", p.isWinner());
            pMap.put("token", p.getToken()); // Pentru identificare in frontend

            // Aratam cartile doar la Showdown sau daca e userul curent
            if (round.equals("showdown") && !p.getStatus().equals("folded")) {
                pMap.put("hand", p.getHand());
                pMap.put("evaluatedHand", p.getEvaluatedHand());
            } else if (p.getToken().equals(userToken)) {
                // Nu punem mana aici, o trimitem separat prin mesajul "poker_hand"
                // Dar frontend-ul tau pare sa astepte mana in lista de jucatori la showdown
                pMap.put("hand", null);
            } else {
                pMap.put("hand", null);
            }
            publicPlayers.add(pMap);
        }
        state.put("players", publicPlayers);

        return state;
    }
}