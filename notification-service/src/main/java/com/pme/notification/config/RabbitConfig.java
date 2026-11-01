package com.pme.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/**
 * Topologie RabbitMQ du consommateur :
 * community.events (topic) --listing.published--> notification.listing-published (quorum)
 * en cas d'échec après les tentatives configurées : community.events.dlx --> notification.listing-published.dlq
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "community.events";
    public static final String ROUTING_KEY = "listing.published";
    public static final String QUEUE = "notification.listing-published";
    public static final String DLX = "community.events.dlx";
    public static final String DLQ = "notification.listing-published.dlq";

    @Bean
    TopicExchange communityEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    /** File quorum : répliquée et durable, adaptée à un cluster RabbitMQ multi-nœuds. */
    @Bean
    Queue listingPublishedQueue() {
        return QueueBuilder.durable(QUEUE)
                .quorum()
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ)
                .build();
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).quorum().build();
    }

    @Bean
    Binding listingPublishedBinding() {
        return BindingBuilder.bind(listingPublishedQueue()).to(communityEventsExchange()).with(ROUTING_KEY);
    }

    @Bean
    Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DLQ);
    }

    /**
     * Désérialisation selon le type du paramètre du listener (type déduit, comportement par défaut
     * de Spring AMQP) et non selon l'en-tête __TypeId__ du producteur ; seul le package du contrat
     * consommé est déclaré fiable.
     */
    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper, "com.pme.notification.messaging");
    }
}
