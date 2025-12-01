package com.proiect.chatgames.config;

import com.proiect.chatgames.websocket.MainWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private MainWebSocketHandler mainWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Aici spunem ca orice conexiune la /ws va fi gestionata de Handler-ul nostru
        registry.addHandler(mainWebSocketHandler, "/ws")
                .setAllowedOrigins("*"); // Permitem conexiuni de oriunde (important pentru dezvoltare)
    }
}