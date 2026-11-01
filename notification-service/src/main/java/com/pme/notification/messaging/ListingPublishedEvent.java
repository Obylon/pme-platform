package com.pme.notification.messaging;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Contrat consommé depuis community-service (routing key "listing.published").
 * Copie volontaire côté consommateur (pas de librairie partagée) : les champs inconnus
 * sont ignorés pour tolérer les évolutions rétrocompatibles du producteur.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ListingPublishedEvent(
        UUID listingId,
        String ownerId,
        String title,
        String category,
        String type,
        Set<String> keywords,
        Instant publishedAt) {
}
