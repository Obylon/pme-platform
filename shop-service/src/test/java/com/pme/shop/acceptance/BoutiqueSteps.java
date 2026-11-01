package com.pme.shop.acceptance;

import static com.pme.shop.TestJwt.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pme.shop.cart.CartItemRepository;
import com.pme.shop.catalog.Product;
import com.pme.shop.catalog.ProductRepository;
import com.pme.shop.order.CustomerOrderRepository;
import com.pme.shop.subscription.SubscriptionRepository;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Définitions d'étapes de la boutique (US-203) et de l'abonnement (US-301). */
public class BoutiqueSteps {

    private static final String USER = "alice";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    ProductRepository products;
    @Autowired
    CartItemRepository cartItems;
    @Autowired
    CustomerOrderRepository orders;
    @Autowired
    SubscriptionRepository subscriptions;

    private UUID productId;
    private MvcResult lastResult;

    @Before
    public void clean() {
        cartItems.deleteAll();
        orders.deleteAll();
        subscriptions.deleteAll();
    }

    @Given("un produit dont le stock est {int}")
    public void unProduit(int stock) {
        productId = products.save(new Product(UUID.randomUUID(), "ACC-" + UUID.randomUUID().toString().substring(0, 8),
                "Figurine Orc", "FIGURINE", 5990, stock, true)).getId();
    }

    @Given("un panier contenant {int} exemplaire(s) de ce produit")
    public void unPanier(int quantity) throws Exception {
        int status = mvc.perform(post("/api/shop/cart/items").with(member(USER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(200);
    }

    @Given("un panier vide")
    public void unPanierVide() {
        // Le panier est vidé avant chaque scénario.
    }

    @When("le membre passe commande")
    public void passeCommande() throws Exception {
        lastResult = mvc.perform(post("/api/shop/orders").with(member(USER))).andReturn();
    }

    @Then("la réponse est {int}")
    public void reponse(int status) {
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(status);
    }

    @Then("une commande au statut {string} est créée")
    public void commandeCreee(String status) throws Exception {
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json.readTree(lastResult.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo(status);
    }

    @Then("le stock du produit passe à {int}")
    @Then("le stock du produit reste à {int}")
    public void stock(int expected) {
        assertThat(products.findById(productId).orElseThrow().getStock()).isEqualTo(expected);
    }

    @Then("le panier est vidé")
    public void panierVide() throws Exception {
        JsonNode cart = json.readTree(mvc.perform(get("/api/shop/cart").with(member(USER)))
                .andReturn().getResponse().getContentAsString());
        assertThat(cart.get("lines").isEmpty()).isTrue();
    }

    @When("le membre souscrit la formule {string}")
    public void souscrit(String plan) throws Exception {
        lastResult = mvc.perform(post("/api/shop/subscriptions").with(member(USER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"" + plan + "\"}")).andReturn();
    }

    @Then("un abonnement actif est créé jusqu'à aujourd'hui plus un an")
    public void abonnementCree() throws Exception {
        JsonNode body = json.readTree(lastResult.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(LocalDate.parse(body.get("endDate").asText())).isEqualTo(LocalDate.now().plusYears(1));
    }

    @Then("la date de fin est prolongée d'un an supplémentaire")
    public void prolonge() throws Exception {
        JsonNode body = json.readTree(lastResult.getResponse().getContentAsString());
        assertThat(LocalDate.parse(body.get("endDate").asText())).isEqualTo(LocalDate.now().plusYears(2));
    }
}
