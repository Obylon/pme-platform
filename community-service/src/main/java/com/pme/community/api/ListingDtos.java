package com.pme.community.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.pme.community.domain.Category;
import com.pme.community.domain.Listing;
import com.pme.community.domain.ListingStatus;
import com.pme.community.domain.ListingType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ListingDtos {

    private ListingDtos() {
    }

    public record CreateListingRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 2000) String description,
            @NotNull Category category,
            @NotNull ListingType type,
            @Size(max = Listing.MAX_KEYWORDS) Set<@Size(max = 30) String> keywords) {
    }

    public record ModerationRequest(@NotBlank @Size(max = 500) String reason) {
    }

    /** Vue publique : l'identifiant du propriétaire est exposé pour permettre la prise de contact via le chat (v1.1). */
    public record ListingResponse(UUID id, String title, String description, Category category, ListingType type,
                                  Set<String> keywords, String ownerId, ListingStatus status, Instant createdAt) {

        public static ListingResponse from(Listing l) {
            return new ListingResponse(l.getId(), l.getTitle(), l.getDescription(), l.getCategory(), l.getType(),
                    l.getKeywords(), l.getOwnerId(), l.getStatus(), l.getCreatedAt());
        }
    }

    public record ModerationResponse(UUID id, ListingStatus status, String moderatedBy, String reason) {

        public static ModerationResponse from(Listing l) {
            return new ModerationResponse(l.getId(), l.getStatus(), l.getModeratedBy(), l.getModerationReason());
        }
    }
}
