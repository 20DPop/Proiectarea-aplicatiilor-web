package com.proiect.chatgames.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()) // Dezactivam CSRF pentru ca folosim JWT
                .authorizeHttpRequests(auth -> auth
                        // Permitem acces liber la Auth (Login/Register), WebSocket si API-ul de jocuri
                        .requestMatchers("/api/auth/**", "/ws/**", "/api/hangman/**").permitAll()
                        // Orice alt endpoint necesita autentificare
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    // Folosim BCrypt pentru a cripta parolele in baza de date
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}