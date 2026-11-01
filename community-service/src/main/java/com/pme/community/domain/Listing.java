package com.pme.community.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.pme.common.web.BusinessConflictException;
import com.pme.common.web.ForbiddenOperationException;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Annonce d'échange ou de don publiée par un membre.
 * Les règles métier (propriété, transitions de statut) sont portées par l'entité.
 */
@Entity
@Table(name = "listing")
public class Listing {

    public static final int MAX_KEYWORDS = 10;

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingType type;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "listing_keyword", joinColumns = @JoinColumn(name = "listing_id"))
    @Column(name = "keyword", nullable = false, length = 30)
    private Set<String> keywords = new HashSet<>();

    /** Identifiant Keycloak (claim sub) du propriétaire. */
    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingStatus status;

    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;

    @Column(name = "moderated_by", length = 64)
    private String moderatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Listing() {
        // JPA
    }

    public static Listing publish(String ownerId, String title, String description, Category category,
                                  ListingType type, Collection<String> keywords, Instant now) {
        Listing listing = new Listing();
        listing.id = UUID.randomUUID();
        listing.ownerId = Objects.requireNonNull(ownerId);
        listing.title = title.strip();
        listing.description = description == null ? null : description.strip();
        listing.category = Objects.requireNonNull(category);
        listing.type = Objects.requireNonNull(type);
        listing.keywords = normalizeKeywords(keywords);
        listing.status = ListingStatus.PUBLISHED;
        listing.createdAt = now;
        listing.updatedAt = now;
        return listing;
    }

    /** Mots-clés en minuscules, sans doublons ni vides, limités en nombre. */
    static Set<String> normalizeKeywords(Collection<String> keywords) {
        if (keywords == null) {
            return new HashSet<>();
        }
        return keywords.stream()
                .filter(Objects::nonNull)
                .map(k -> k.strip().toLowerCase(Locale.ROOT))
                .filter(k -> !k.isEmpty())
                .limit(MAX_KEYWORDS)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /** Retrait par le propriétaire uniquement. */
    public void withdraw(String userId, Instant now) {
        if (!ownerId.equals(userId)) {
            throw new ForbiddenOperationException("Seul le propriétaire peut retirer son annonce");
        }
        ensurePublished();
        status = ListingStatus.REMOVED;
        updatedAt = now;
    }

    /** Retrait par un modérateur, motif obligatoire et tracé (non-répudiation). */
    public void moderate(String moderatorId, String reason, Instant now) {
        ensurePublished();
        status = ListingStatus.MODERATED;
        moderatedBy = Objects.requireNonNull(moderatorId);
        moderationReason = Objects.requireNonNull(reason).strip();
        updatedAt = now;
    }

    private void ensurePublished() {
        if (status != ListingStatus.PUBLISHED) {
            throw new BusinessConflictException("L'annonce n'est plus publiée");
        }
    }

    public boolean isPublished() {
        return status == ListingStatus.PUBLISHED;
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public ListingType getType() { return type; }
    public Set<String> getKeywords() { return Set.copyOf(keywords); }
    public String getOwnerId() { return ownerId; }
    public ListingStatus getStatus() { return status; }
    public String getModerationReason() { return moderationReason; }
    public String getModeratedBy() { return moderatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
