package com.pme.notification.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pme.notification.messaging.ListingPublishedEvent;

@Service
public class NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationPreferenceRepository preferences;
    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationPreferenceRepository preferences, NotificationRepository notifications,
                               Clock clock) {
        this.preferences = preferences;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Crée une notification pour chaque membre intéressé.
     * Idempotent : un message redélivré par RabbitMQ ne crée pas de doublon.
     * Limite connue (v1) : parcours de toutes les préférences actives, à remplacer par une
     * requête indexée par catégorie / mot-clé quand le nombre de membres grandira.
     */
    @Transactional
    public int onListingPublished(ListingPublishedEvent event) {
        Instant now = Instant.now(clock);
        int created = 0;
        for (NotificationPreference preference : preferences.findByEnabledTrue()) {
            if (preference.matches(event)
                    && !notifications.existsByUserIdAndListingId(preference.getUserId(), event.listingId())) {
                notifications.save(new Notification(preference.getUserId(), event.listingId(),
                        "Nouvelle annonce susceptible de vous intéresser : " + event.title(), now));
                created++;
            }
        }
        LOG.info("listing.published traité listing={} notifications={}", event.listingId(), created);
        return created;
    }

    @Transactional
    public NotificationPreference updatePreferences(String userId, Collection<String> categories,
                                                    Collection<String> keywords, boolean enabled) {
        NotificationPreference preference = preferences.findById(userId).orElseGet(() -> new NotificationPreference(userId));
        preference.update(categories, keywords, enabled, Instant.now(clock));
        return preferences.save(preference);
    }

    @Transactional(readOnly = true)
    public NotificationPreference getPreferences(String userId) {
        return preferences.findById(userId).orElseGet(() -> new NotificationPreference(userId));
    }

    @Transactional(readOnly = true)
    public Page<Notification> myNotifications(String userId, Pageable pageable) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
}
