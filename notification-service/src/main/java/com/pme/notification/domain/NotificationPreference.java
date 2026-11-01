package com.pme.notification.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.pme.notification.messaging.ListingPublishedEvent;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/** Centres d'intérêt d'un membre et activation de ses notifications (US-105). */
@Entity
@Table(name = "notification_preference")
public class NotificationPreference {

    public static final int MAX_ITEMS = 20;

    @Id
    @Column(name = "user_id", length = 64)
    private String userId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "preference_category", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "category", nullable = false, length = 20)
    private Set<String> categories = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "preference_keyword", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "keyword", nullable = false, length = 30)
    private Set<String> keywords = new HashSet<>();

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
        // JPA
    }

    public NotificationPreference(String userId) {
        this.userId = Objects.requireNonNull(userId);
    }

    public void update(Collection<String> newCategories, Collection<String> newKeywords, boolean enable, Instant now) {
        this.categories = normalize(newCategories, Locale.ROOT, true);
        this.keywords = normalize(newKeywords, Locale.ROOT, false);
        this.enabled = enable;
        this.updatedAt = now;
    }

    private static Set<String> normalize(Collection<String> values, Locale locale, boolean upper) {
        if (values == null) {
            return new HashSet<>();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(v -> !v.isEmpty())
                .map(v -> upper ? v.toUpperCase(locale) : v.toLowerCase(locale))
                .limit(MAX_ITEMS)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Règle de correspondance (US-106) : notifications actives, annonce d'un autre membre,
     * et catégorie suivie OU mot-clé suivi présent dans les mots-clés ou le titre de l'annonce.
     */
    public boolean matches(ListingPublishedEvent event) {
        if (!enabled || userId.equals(event.ownerId())) {
            return false;
        }
        if (event.category() != null && categories.contains(event.category().toUpperCase(Locale.ROOT))) {
            return true;
        }
        String title = event.title() == null ? "" : event.title().toLowerCase(Locale.ROOT);
        Set<String> listingKeywords = event.keywords() == null ? Set.of() : event.keywords();
        return keywords.stream().anyMatch(k -> listingKeywords.contains(k) || title.contains(k));
    }

    public String getUserId() { return userId; }
    public Set<String> getCategories() { return Set.copyOf(categories); }
    public Set<String> getKeywords() { return Set.copyOf(keywords); }
    public boolean isEnabled() { return enabled; }
    public Instant getUpdatedAt() { return updatedAt; }
}
