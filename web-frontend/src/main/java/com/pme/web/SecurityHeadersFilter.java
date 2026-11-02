package com.pme.web;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * En-têtes de sécurité du site.
 * <ul>
 *   <li>Content-Security-Policy stricte : scripts, styles et appels réseau limités au domaine
 *       (les API et Keycloak sont servis par la même Gateway), aucun script en ligne, pas d'intégration
 *       dans un cadre tiers ;</li>
 *   <li>pas de détection de type MIME, référent minimal, fonctions du navigateur inutiles désactivées ;</li>
 *   <li>cache : la page d'accueil n'est jamais mise en cache (une nouvelle version est prise immédiatement),
 *       les scripts, styles et images le sont une heure.</li>
 * </ul>
 * HSTS est ajouté par Spring uniquement en HTTPS ; ici le TLS est terminé par la Gateway, il est donc posé explicitement.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self' data:",
            "connect-src 'self'",
            "font-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        String path = request.getRequestURI();
        boolean page = "/".equals(path) || path.endsWith(".html");
        response.setHeader("Cache-Control", page ? "no-cache" : "max-age=3600");
        chain.doFilter(request, response);
    }
}
