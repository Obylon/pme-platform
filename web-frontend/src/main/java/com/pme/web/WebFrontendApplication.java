package com.pme.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sert le site web (fichiers statiques). Le navigateur s'authentifie directement auprès de Keycloak
 * (Authorization Code + PKCE, client public pme-web) et appelle les API sur le même domaine :
 * ce service ne voit ni mot de passe ni jeton.
 */
@SpringBootApplication
public class WebFrontendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebFrontendApplication.class, args);
    }
}
