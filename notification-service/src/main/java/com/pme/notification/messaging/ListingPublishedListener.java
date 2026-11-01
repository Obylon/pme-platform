package com.pme.notification.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.pme.notification.config.RabbitConfig;
import com.pme.notification.domain.NotificationService;

/**
 * Consommateur des annonces publiées. En cas d'exception, Spring AMQP réessaie
 * (spring.rabbitmq.listener.simple.retry) puis rejette le message, qui part en DLQ.
 */
@Component
public class ListingPublishedListener {

    private final NotificationService service;

    public ListingPublishedListener(NotificationService service) {
        this.service = service;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onListingPublished(ListingPublishedEvent event) {
        service.onListingPublished(event);
    }
}
