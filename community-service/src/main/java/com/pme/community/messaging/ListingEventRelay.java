package com.pme.community.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.pme.community.config.RabbitConfig;
import com.pme.community.domain.ListingPublishedEvent;

/**
 * Relaie les événements de domaine vers RabbitMQ une fois la transaction validée.
 * Limite connue (v1) : si le broker est indisponible à cet instant, l'événement est perdu
 * (journalisé en erreur). Le plan de remédiation prévoit le pattern Transactional Outbox.
 */
@Component
public class ListingEventRelay {

    private static final Logger LOG = LoggerFactory.getLogger(ListingEventRelay.class);

    private final RabbitTemplate rabbitTemplate;

    public ListingEventRelay(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onListingPublished(ListingPublishedEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.LISTING_PUBLISHED, event);
        } catch (AmqpException ex) {
            LOG.error("Événement listing.published non transmis pour l'annonce {}", event.listingId(), ex);
        }
    }
}
