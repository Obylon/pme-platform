package com.pme.community.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.pme.common.security.ApiSecurity;

@Configuration
@EnableMethodSecurity
public class AppConfig {

    /** Lecture publique des annonces ; toute écriture exige un jeton valide. */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return ApiSecurity.build(http, "/api/community/listings", "/api/community/listings/*");
    }

    @Bean
    @Primary
    Clock clock() {
        return Clock.systemUTC();
    }
}
