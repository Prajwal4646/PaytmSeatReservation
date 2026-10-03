package com.example.seats.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfig {

    /** Jackson 2 mapper used by AuthService (JWT) and RequestParser (request bodies). */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}