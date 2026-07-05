package com.f1telemetry.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures the Spring WebSocket + STOMP message broker.
 * This sets up the endpoints where the frontend UI will connect to receive 30Hz live updates.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Use a simple in-memory message broker to carry messages back to the client on destinations prefixed with "/topic"
        config.enableSimpleBroker("/topic");
        
        // Prefix for messages bound for methods annotated with @MessageMapping (if we had client->server commands)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // The endpoint the frontend connects to. SockJS provides fallback options for older browsers.
        registry.addEndpoint("/telemetry-websocket").setAllowedOriginPatterns("*").withSockJS();
    }
}
