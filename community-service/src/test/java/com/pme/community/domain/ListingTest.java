package com.pme.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.pme.common.web.BusinessConflictException;
import com.pme.common.web.ForbiddenOperationException;

class ListingTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    private static Listing aliceListing() {
        return Listing.publish("alice", "  Figurine Orc  ", "Édition festival", Category.FIGURINE,
                ListingType.EXCHANGE, List.of("Orc", " orc ", "Evil Ed", ""), NOW);
    }

    @Test
    void publishNormalizesTitleAndKeywords() {
        Listing listing = aliceListing();

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.PUBLISHED);
        assertThat(listing.getTitle()).isEqualTo("Figurine Orc");
        assertThat(listing.getKeywords()).containsExactlyInAnyOrder("orc", "evil ed");
        assertThat(listing.getOwnerId()).isEqualTo("alice");
    }

    @Test
    void keywordsAreCapped() {
        String[] many = IntStream.range(0, 20).mapToObj(i -> "k" + i).toArray(String[]::new);

        assertThat(Listing.normalizeKeywords(Arrays.asList(many))).hasSize(Listing.MAX_KEYWORDS);
        assertThat(Listing.normalizeKeywords(null)).isEmpty();
    }

    @Test
    void ownerCanWithdraw() {
        Listing listing = aliceListing();

        listing.withdraw("alice", NOW);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.REMOVED);
    }

    @Test
    void otherMemberCannotWithdraw() {
        Listing listing = aliceListing();

        assertThatThrownBy(() -> listing.withdraw("bob", NOW)).isInstanceOf(ForbiddenOperationException.class);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.PUBLISHED);
    }

    @Test
    void moderationIsTraced() {
        Listing listing = aliceListing();

        listing.moderate("modo", "Contrefaçon", NOW);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.MODERATED);
        assertThat(listing.getModeratedBy()).isEqualTo("modo");
        assertThat(listing.getModerationReason()).isEqualTo("Contrefaçon");
    }

    @Test
    void cannotModerateAnAlreadyRemovedListing() {
        Listing listing = aliceListing();
        listing.withdraw("alice", NOW);

        assertThatThrownBy(() -> listing.moderate("modo", "x", NOW)).isInstanceOf(BusinessConflictException.class);
    }
}
