package com.pme.notification.messaging;

import static com.pme.notification.TestJwt.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.pme.notification.TestcontainersConfig;
import com.pme.notification.config.RabbitConfig;
import com.pme.notification.domain.NotificationPreferenceRepository;
import com.pme.notification.domain.NotificationRepository;

/**
 * Test d'intégration du flux asynchrone de bout en bout (US-106) avec un vrai RabbitMQ :
 * préférences via l'API -> événement publié sur l'échange -> notification consultable via l'API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ListingPublishedFlowIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    NotificationPreferenceRepository preferences;

    @BeforeEach
    void clean() {
        notifications.deleteAll();
        preferences.deleteAll();
    }

    private void interested(String user, String json) throws Exception {
        mvc.perform(put("/api/notifications/preferences/me").with(member(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void interestedMemberIsNotifiedAsynchronously() throws Exception {
        interested("bob", "{\"categories\":[\"FIGURINE\"],\"keywords\":[],\"enabled\":true}");
        interested("carol", "{\"categories\":[\"FIGURINE\"],\"keywords\":[],\"enabled\":false}");
        UUID listingId = UUID.randomUUID();

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY,
                new ListingPublishedEvent(listingId, "alice", "Figurine Orc", "FIGURINE", "EXCHANGE",
                        Set.of("orc"), Instant.now()));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mvc.perform(get("/api/notifications/me").with(member("bob")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.content[0].listingId").value(listingId.toString())));
        mvc.perform(get("/api/notifications/me").with(member("carol")))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/notifications/me").with(member("alice")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void malformedMessageGoesToDeadLetterQueue() {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message poison = MessageBuilder.withBody("{\"listingId\":\"pas-un-uuid\"}".getBytes()).andProperties(props).build();

        rabbitTemplate.send(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, poison);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(rabbitTemplate.receive(RabbitConfig.DLQ)).isNotNull());
    }

    @Test
    void notificationsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/notifications/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void preferencesPayloadIsValidated() throws Exception {
        mvc.perform(put("/api/notifications/preferences/me").with(member("bob"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categories\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
