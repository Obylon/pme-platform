package com.pme.notification;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Arrays;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Jetons JWT simulés (signature non vérifiée en test) pour exercer les règles d'autorisation. */
public final class TestJwt {

    private TestJwt() {
    }

    public static RequestPostProcessor user(String subject, String... roles) {
        return jwt().jwt(j -> j.subject(subject))
                .authorities(Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    public static RequestPostProcessor member(String subject) {
        return user(subject, "member");
    }

    public static RequestPostProcessor moderator(String subject) {
        return user(subject, "member", "moderator");
    }
}
