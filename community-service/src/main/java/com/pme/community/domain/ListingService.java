package com.pme.community.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pme.common.web.ResourceNotFoundException;

@Service
public class ListingService {

    private static final Logger LOG = LoggerFactory.getLogger(ListingService.class);

    private final ListingRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ListingService(ListingRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Enregistre l'annonce puis émet l'événement de domaine.
     * Le relais vers RabbitMQ n'a lieu qu'après le commit (voir ListingEventRelay) :
     * aucun événement n'est publié pour une annonce dont l'enregistrement aurait échoué.
     */
    @Transactional
    public Listing publish(String ownerId, String title, String description, Category category,
                           ListingType type, Collection<String> keywords) {
        Listing listing = repository.save(
                Listing.publish(ownerId, title, description, category, type, keywords, Instant.now(clock)));
        events.publishEvent(ListingPublishedEvent.from(listing));
        LOG.info("Annonce publiée id={} catégorie={} type={}", listing.getId(), category, type);
        return listing;
    }

    @Transactional(readOnly = true)
    public Page<Listing> search(Category category, ListingType type, Pageable pageable) {
        return repository.findAll(ListingRepository.isPublished()
                .and(ListingRepository.hasCategory(category))
                .and(ListingRepository.hasType(type)), pageable);
    }

    @Transactional(readOnly = true)
    public Listing getPublished(UUID id) {
        return repository.findById(id)
                .filter(Listing::isPublished)
                .orElseThrow(() -> new ResourceNotFoundException("Annonce introuvable"));
    }

    @Transactional
    public void withdraw(UUID id, String userId) {
        find(id).withdraw(userId, Instant.now(clock));
        LOG.info("Annonce retirée par son propriétaire id={}", id);
    }

    @Transactional
    public Listing moderate(UUID id, String moderatorId, String reason) {
        Listing listing = find(id);
        listing.moderate(moderatorId, reason, Instant.now(clock));
        // Journal d'audit : qui, quoi, pourquoi
        LOG.info("AUDIT moderation listing={} moderator={} reason=\"{}\"", id, moderatorId, reason);
        return listing;
    }

    private Listing find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Annonce introuvable"));
    }
}
