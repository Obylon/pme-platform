package com.pme.notification.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.pme.common.security.ApiSecurity;

@Configuration
@EnableMethodSecurity
public class AppConfig {

    /** Aucune donnée publique : toutes les routes métier exigent un jeton. */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return ApiSecurity.build(http, "/api/notifications/public/**");
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
