package com.pme.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtAuthConverterTest {

    private final KeycloakJwtAuthConverter converter = new KeycloakJwtAuthConverter();

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("user-123")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
    }

    @Test
    void mapsRealmRolesToSpringAuthorities() {
        Jwt jwt = token().claim("realm_access", Map.of("roles", List.of("member", "moderator"))).build();

        var auth = converter.convert(jwt);

        assertThat(auth.getName()).isEqualTo("user-123");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_member", "ROLE_moderator");
    }

    @Test
    void noRealmAccessMeansNoAuthority() {
        Jwt jwt = token().claim("scope", "openid").build();

        assertThat(converter.convert(jwt).getAuthorities()).isEmpty();
    }

    @Test
    void malformedRolesClaimIsIgnored() {
        Jwt jwt = token().claim("realm_access", Map.of("roles", "admin")).build();

        assertThat(converter.convert(jwt).getAuthorities()).isEmpty();
    }
}
