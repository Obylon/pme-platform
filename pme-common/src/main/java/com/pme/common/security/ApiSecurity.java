package com.pme.common.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Politique de sécurité commune à toutes les API (resource servers OAuth2) :
 * <ul>
 *   <li>authentification par jeton Bearer JWT uniquement, sans session (stateless) ;</li>
 *   <li>CSRF désactivé : aucun cookie d'authentification, donc pas de vecteur CSRF ;</li>
 *   <li>tout est refusé par défaut, sauf les sondes, la documentation et les lectures publiques déclarées ;</li>
 *   <li>en-têtes de sécurité par défaut de Spring Security (HSTS, X-Content-Type-Options, X-Frame-Options...).</li>
 * </ul>
 * Les règles fines (rôles, propriété des ressources) sont vérifiées au niveau des méthodes (@PreAuthorize)
 * et dans le domaine.
 */
public final class ApiSecurity {

    private ApiSecurity() {
    }

    public static SecurityFilterChain build(HttpSecurity http, String... publicGetPaths) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.GET, publicGetPaths).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtAuthConverter())));
        return http.build();
    }
}
