package com.pme.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.pme.common.web.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock
    ListingRepository repository;
    @Mock
    ApplicationEventPublisher events;

    ListingService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);
        service = new ListingService(repository, events, clock);
    }

    @Test
    void publishSavesAndEmitsDomainEvent() {
        when(repository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        Listing listing = service.publish("alice", "Figurine Orc", null, Category.FIGURINE,
                ListingType.GIVEAWAY, List.of("orc"));

        ArgumentCaptor<ListingPublishedEvent> captor = ArgumentCaptor.forClass(ListingPublishedEvent.class);
        verify(events).publishEvent(captor.capture());
        ListingPublishedEvent event = captor.getValue();
        assertThat(event.listingId()).isEqualTo(listing.getId());
        assertThat(event.ownerId()).isEqualTo("alice");
        assertThat(event.category()).isEqualTo("FIGURINE");
        assertThat(event.keywords()).containsExactly("orc");
    }

    @Test
    void withdrawnListingIsNotPubliclyVisible() {
        Listing listing = Listing.publish("alice", "t", null, Category.BD, ListingType.EXCHANGE, List.of(),
                Instant.now());
        listing.withdraw("alice", Instant.now());
        when(repository.findById(listing.getId())).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.getPublished(listing.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void moderatingUnknownListingFails() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moderate(id, "modo", "spam"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(events, never()).publishEvent(any(Object.class));
    }
}
