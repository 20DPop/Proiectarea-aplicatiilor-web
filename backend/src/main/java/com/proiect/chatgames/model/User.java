package com.proiect.chatgames.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// @Document spune ca aceasta clasa va fi o colectie in MongoDB numita "users"
@Document(collection = "users")
// @Data (Lombok) genereaza automat Getteri, Setteri, toString, etc.
@Data
@NoArgsConstructor
public class User {
    @Id
    private String id;

    private String username;
    private String password;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }
}