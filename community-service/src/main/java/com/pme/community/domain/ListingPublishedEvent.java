package com.pme.community.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Événement de domaine émis à la publication d'une annonce.
 * C'est le contrat d'échange avec notification-service (routing key "listing.published") :
 * toute évolution doit rester rétrocompatible (ajout de champs uniquement).
 */
public record ListingPublishedEvent(
        UUID listingId,
        String ownerId,
        String title,
        String category,
        String type,
        Set<String> keywords,
        Instant publishedAt) {

    public static ListingPublishedEvent from(Listing listing) {
        return new ListingPublishedEvent(listing.getId(), listing.getOwnerId(), listing.getTitle(),
                listing.getCategory().name(), listing.getType().name(), listing.getKeywords(),
                listing.getCreatedAt());
    }
}
