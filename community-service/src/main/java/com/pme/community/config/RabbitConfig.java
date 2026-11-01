package com.pme.community.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "community.events";
    public static final String LISTING_PUBLISHED = "listing.published";

    /** Échange topic durable : les consommateurs s'y abonnent sans que ce service les connaisse. */
    @Bean
    TopicExchange communityEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /**
     * Seul le package du contrat d'événements est autorisé à la désérialisation
     * (Spring AMQP refuse par défaut toute classe hors java.lang / java.util).
     */
    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper, "com.pme.community.domain");
        converter.setCreateMessageIds(true);
        return converter;
    }
}
