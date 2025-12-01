package com.proiect.chatgames.controller;

import com.proiect.chatgames.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/hangman")
@CrossOrigin(origins = "*")
public class HangmanController {

    @Autowired
    GameService gameService;

    @GetMapping("/games")
    public Map<String, Object> getGames() {
        return Map.of(
                "success", true,
                "games", gameService.getAllGames()
        );
    }
}