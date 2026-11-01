package com.pme.community.api;

import static com.pme.community.TestJwt.member;
import static com.pme.community.TestJwt.moderator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;
import com.pme.community.TestcontainersConfig;
import com.pme.community.config.RabbitConfig;
import com.pme.community.domain.ListingPublishedEvent;
import com.pme.community.domain.ListingRepository;

/**
 * Tests d'appels de l'API avec PostgreSQL et RabbitMQ réels (Testcontainers).
 * Vérifie aussi les règles d'autorisation (401 / 403) et l'émission de l'événement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CommunityApiIT {

    private static final String TEST_QUEUE = "test.listing-published";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    ListingRepository repository;
    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        Queue queue = new Queue(TEST_QUEUE, false, false, false);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue)
                .to(new TopicExchange(RabbitConfig.EXCHANGE)).with(RabbitConfig.LISTING_PUBLISHED));
        amqpAdmin.purgeQueue(TEST_QUEUE, false);
    }

    private static final String ORC_LISTING = """
            {"title":"Figurine Orc édition festival","description":"Peinte à la main",
             "category":"FIGURINE","type":"EXCHANGE","keywords":["Orc","Evil Ed"]}""";

    private String publishAs(String user, String body) throws Exception {
        String response = mvc.perform(post("/api/community/listings").with(member(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return String.valueOf(json.readValue(response, java.util.Map.class).get("id"));
    }

    @Test
    void publishRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/community/listings").contentType(MediaType.APPLICATION_JSON).content(ORC_LISTING))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publishRequiresMemberRole() throws Exception {
        mvc.perform(post("/api/community/listings").with(com.pme.community.TestJwt.user("eve"))
                        .contentType(MediaType.APPLICATION_JSON).content(ORC_LISTING))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishCreatesListingAndEmitsEvent() throws Exception {
        String id = publishAs("alice", ORC_LISTING);

        mvc.perform(get("/api/community/listings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.ownerId").value("alice"));

        Object message = rabbitTemplate.receiveAndConvert(TEST_QUEUE, 5000);
        assertThat(message).isInstanceOf(ListingPublishedEvent.class);
        assertThat(((ListingPublishedEvent) message).listingId()).hasToString(id);
    }

    @Test
    void invalidListingIsRejected() throws Exception {
        mvc.perform(post("/api/community/listings").with(member("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"category\":\"FIGURINE\",\"type\":\"EXCHANGE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/community/listings").with(member("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"ok\",\"category\":\"INCONNUE\",\"type\":\"EXCHANGE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchFiltersByCategoryAndHidesRemovedListings() throws Exception {
        publishAs("alice", ORC_LISTING);
        String bluray = publishAs("bob", """
                {"title":"Blu-ray Riflesso","category":"BLURAY","type":"GIVEAWAY"}""");
        mvc.perform(delete("/api/community/listings/{id}", bluray).with(member("bob")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/community/listings").param("category", "FIGURINE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].category").value("FIGURINE"));
        mvc.perform(get("/api/community/listings"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void onlyOwnerCanWithdraw() throws Exception {
        String id = publishAs("alice", ORC_LISTING);

        mvc.perform(delete("/api/community/listings/{id}", id).with(member("bob")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/community/listings/{id}", id).with(member("alice")))
                .andExpect(status().isNoContent());
    }

    @Test
    void onlyModeratorCanModerate() throws Exception {
        String id = publishAs("alice", ORC_LISTING);
        String body = "{\"reason\":\"Contrefaçon\"}";

        mvc.perform(post("/api/community/listings/{id}/moderation", id).with(member("bob"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/community/listings/{id}/moderation", id).with(moderator("modo"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MODERATED"))
                .andExpect(jsonPath("$.moderatedBy").value("modo"));
    }

    @Test
    void invalidSortIsABadRequestNotAServerError() throws Exception {
        mvc.perform(get("/api/community/listings").param("sort", "doesNotExist"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthProbesArePublic() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

}
