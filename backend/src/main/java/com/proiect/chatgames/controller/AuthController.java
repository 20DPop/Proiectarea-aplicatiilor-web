package com.proiect.chatgames.controller;

import com.proiect.chatgames.model.User;
import com.proiect.chatgames.repository.UserRepository;
import com.proiect.chatgames.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
// Permitem conexiuni de la frontend-ul tau React (portul standard e 5173 pentru Vite, sau 3000 pentru Create React App)
// Daca rulezi React pe alt port, schimba aici. Pun "*" pentru siguranta momentan.
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        User user = userRepository.findByUsername(username).orElse(null);

        if (user != null && encoder.matches(password, user.getPassword())) {
            String token = jwtUtils.generateJwtToken(username);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "token", token,
                    "username", username
            ));
        }

        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Username sau parola gresite"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Username deja existent"));
        }

        User user = new User(username, encoder.encode(password));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("success", true, "message", "Utilizator creat!"));
    }
}