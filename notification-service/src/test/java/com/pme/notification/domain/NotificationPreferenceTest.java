package com.pme.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pme.notification.messaging.ListingPublishedEvent;

class NotificationPreferenceTest {

    private static ListingPublishedEvent listing(String owner, String category, String title, Set<String> keywords) {
        return new ListingPublishedEvent(UUID.randomUUID(), owner, title, category, "EXCHANGE", keywords, Instant.now());
    }

    private static NotificationPreference pref(String user, List<String> categories, List<String> keywords, boolean enabled) {
        NotificationPreference p = new NotificationPreference(user);
        p.update(categories, keywords, enabled, Instant.now());
        return p;
    }

    @Test
    void matchesOnCategoryCaseInsensitive() {
        assertThat(pref("bob", List.of("figurine"), List.of(), true)
                .matches(listing("alice", "FIGURINE", "Orc", Set.of()))).isTrue();
    }

    @Test
    void matchesOnKeywordInListingKeywordsOrTitle() {
        NotificationPreference bob = pref("bob", List.of(), List.of(" ORC "), true);

        assertThat(bob.matches(listing("alice", "BD", "Tome 1", Set.of("orc")))).isTrue();
        assertThat(bob.matches(listing("alice", "BD", "L'Orc - tome 1", Set.of()))).isTrue();
        assertThat(bob.matches(listing("alice", "BD", "Zombies", Set.of("zombie")))).isFalse();
    }

    @Test
    void neverNotifiesTheOwner() {
        assertThat(pref("alice", List.of("FIGURINE"), List.of(), true)
                .matches(listing("alice", "FIGURINE", "Orc", Set.of()))).isFalse();
    }

    @Test
    void disabledPreferencesNeverMatch() {
        assertThat(pref("carol", List.of("FIGURINE"), List.of(), false)
                .matches(listing("alice", "FIGURINE", "Orc", Set.of()))).isFalse();
    }

    @Test
    void nullFieldsInEventAreTolerated() {
        assertThat(pref("bob", List.of("FIGURINE"), List.of("orc"), true)
                .matches(listing("alice", null, null, null))).isFalse();
    }
}
