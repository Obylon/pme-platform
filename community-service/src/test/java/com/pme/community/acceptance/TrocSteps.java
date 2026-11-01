package com.pme.community.acceptance;

import static com.pme.community.TestJwt.member;
import static com.pme.community.TestJwt.moderator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pme.community.domain.Listing;
import com.pme.community.domain.ListingRepository;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Définitions d'étapes des scénarios de troc (US-101, US-103, US-104). */
public class TrocSteps {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    ListingRepository repository;

    private MvcResult lastResult;
    private UUID listingId;

    @Before
    public void cleanDatabase() {
        repository.deleteAll();
    }

    @Given("un membre authentifié {string}")
    public void unMembreAuthentifie(String user) {
        // L'authentification est simulée à chaque requête par un JWT de test portant le rôle "member".
    }

    @Given("un internaute non authentifié")
    public void unInternauteNonAuthentifie() {
        // Aucune information d'authentification n'est jointe aux requêtes.
    }

    @When("{string} publie une annonce {string} de type {string} dans la catégorie {string}")
    public void publie(String user, String title, String type, String category) throws Exception {
        lastResult = mvc.perform(post("/api/community/listings").with(member(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(title, type, category))).andReturn();
        if (lastResult.getResponse().getStatus() == 201) {
            listingId = UUID.fromString(json.readTree(lastResult.getResponse().getContentAsString())
                    .get("id").asText());
        }
    }

    @When("il publie une annonce")
    public void ilPublieUneAnnonceSansJeton() throws Exception {
        lastResult = mvc.perform(post("/api/community/listings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Annonce anonyme", "GIVEAWAY", "GOODIES"))).andReturn();
    }

    @Given("une annonce publiée par {string}")
    public void uneAnnoncePubliee(String owner) throws Exception {
        publie(owner, "Figurine Orc édition festival", "EXCHANGE", "FIGURINE");
        assertThat(listingId).isNotNull();
    }

    @When("{string} retire l'annonce")
    public void retire(String user) throws Exception {
        perform(delete("/api/community/listings/{id}", listingId).with(member(user)));
    }

    @When("un modérateur {string} retire l'annonce avec le motif {string}")
    public void modere(String moderatorId, String reason) throws Exception {
        perform(post("/api/community/listings/{id}/moderation", listingId).with(moderator(moderatorId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("reason", reason))));
    }

    @When("{string} tente de modérer l'annonce")
    public void tenteDeModerer(String user) throws Exception {
        perform(post("/api/community/listings/{id}/moderation", listingId).with(member(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("reason", "abus"))));
    }

    @Then("la réponse est {int}")
    public void laReponseEst(int status) {
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(status);
    }

    @Then("l'annonce a le statut {string}")
    public void statut(String status) {
        assertThat(stored().getStatus().name()).isEqualTo(status);
    }

    @Then("{string} en est la propriétaire")
    public void proprietaire(String owner) {
        assertThat(stored().getOwnerId()).isEqualTo(owner);
    }

    @Then("l'annonce n'est plus visible publiquement")
    public void plusVisible() throws Exception {
        int status = mvc.perform(get("/api/community/listings/{id}", listingId)).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(404);
    }

    @Then("le motif {string} et le modérateur {string} sont enregistrés")
    public void moderationTracee(String reason, String moderatorId) {
        Listing listing = stored();
        assertThat(listing.getModerationReason()).isEqualTo(reason);
        assertThat(listing.getModeratedBy()).isEqualTo(moderatorId);
    }

    private void perform(MockHttpServletRequestBuilder request) throws Exception {
        lastResult = mvc.perform(request).andReturn();
    }

    private Listing stored() {
        return repository.findById(listingId).orElseThrow();
    }

    private String body(String title, String type, String category) throws Exception {
        return json.writeValueAsString(java.util.Map.of("title", title, "type", type, "category", category,
                "keywords", java.util.List.of("orc")));
    }
}
