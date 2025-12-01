package com.proiect.chatgames.repository;

import com.proiect.chatgames.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

// Extindem MongoRepository pentru a avea acces la metode precum save(), findAll(), etc.
public interface UserRepository extends MongoRepository<User, String> {
    // Spring implementeaza automat aceasta metoda bazandu-se pe nume
    Optional<User> findByUsername(String username);

    // Verificam daca un username exista deja
    Boolean existsByUsername(String username);
}