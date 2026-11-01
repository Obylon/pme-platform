package com.pme.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pme.notification.messaging.ListingPublishedEvent;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationPreferenceRepository preferences;
    @Mock
    NotificationRepository notifications;

    NotificationService service;

    private final ListingPublishedEvent event = new ListingPublishedEvent(UUID.randomUUID(), "alice",
            "Figurine Orc", "FIGURINE", "EXCHANGE", Set.of("orc"), Instant.now());

    @BeforeEach
    void setUp() {
        service = new NotificationService(preferences, notifications,
                Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC));
    }

    private static NotificationPreference pref(String user, String category) {
        NotificationPreference p = new NotificationPreference(user);
        p.update(List.of(category), List.of(), true, Instant.now());
        return p;
    }

    @Test
    void notifiesEveryInterestedMemberExceptTheOwner() {
        when(preferences.findByEnabledTrue()).thenReturn(List.of(pref("bob", "FIGURINE"), pref("alice", "FIGURINE"),
                pref("dan", "BD")));

        int created = service.onListingPublished(event);

        assertThat(created).isEqualTo(1);
        verify(notifications, times(1)).save(any(Notification.class));
    }

    @Test
    void redeliveredEventDoesNotCreateDuplicates() {
        when(preferences.findByEnabledTrue()).thenReturn(List.of(pref("bob", "FIGURINE")));
        when(notifications.existsByUserIdAndListingId(eq("bob"), eq(event.listingId()))).thenReturn(true);

        assertThat(service.onListingPublished(event)).isZero();
        verify(notifications, never()).save(any());
    }
}
