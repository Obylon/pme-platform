package com.pme.shop.api;

import static com.pme.shop.TestJwt.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.pme.shop.TestcontainersConfig;
import com.pme.shop.cart.CartItemRepository;
import com.pme.shop.catalog.Product;
import com.pme.shop.catalog.ProductRepository;
import com.pme.shop.order.CustomerOrderRepository;
import com.pme.shop.subscription.SubscriptionRepository;

/** Tests d'appels de l'API boutique avec un PostgreSQL réel (Testcontainers). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ShopApiIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ProductRepository products;
    @Autowired
    CartItemRepository cartItems;
    @Autowired
    CustomerOrderRepository orders;
    @Autowired
    SubscriptionRepository subscriptions;

    @BeforeEach
    void clean() {
        cartItems.deleteAll();
        orders.deleteAll();
        subscriptions.deleteAll();
    }

    private Product product(int stock) {
        return products.save(new Product(UUID.randomUUID(), "TEST-" + UUID.randomUUID().toString().substring(0, 8),
                "Produit de test", "GOODIES", 1000, stock, false));
    }

    private void addToCart(String user, UUID productId, int quantity) throws Exception {
        mvc.perform(post("/api/shop/cart/items").with(member(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void catalogIsPublic() throws Exception {
        mvc.perform(get("/api/shop/products").param("category", "figurine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("FIG-ORC-001"))
                .andExpect(jsonPath("$.content[0].exclusive").value(true));
    }

    @Test
    void cartRequiresAuthenticationAndValidQuantity() throws Exception {
        mvc.perform(get("/api/shop/cart")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/shop/cart/items").with(member("alice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + product(5).getId() + "\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkoutConfirmsOrderDecrementsStockAndEmptiesCart() throws Exception {
        Product p = product(5);
        addToCart("alice", p.getId(), 2);

        mvc.perform(post("/api/shop/orders").with(member("alice")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalCents").value(2000));

        assertThat(products.findById(p.getId()).orElseThrow().getStock()).isEqualTo(3);
        mvc.perform(get("/api/shop/cart").with(member("alice")))
                .andExpect(jsonPath("$.lines").isEmpty());
    }

    @Test
    void insufficientStockIsAConflictAndStockIsUnchanged() throws Exception {
        Product p = product(1);
        addToCart("alice", p.getId(), 2);

        mvc.perform(post("/api/shop/orders").with(member("alice"))).andExpect(status().isConflict());

        assertThat(products.findById(p.getId()).orElseThrow().getStock()).isEqualTo(1);
    }

    @Test
    void emptyCartCheckoutIsABadRequest() throws Exception {
        mvc.perform(post("/api/shop/orders").with(member("alice"))).andExpect(status().isBadRequest());
    }

    @Test
    void membersOnlySeeTheirOwnOrders() throws Exception {
        Product p = product(10);
        addToCart("alice", p.getId(), 1);
        mvc.perform(post("/api/shop/orders").with(member("alice"))).andExpect(status().isCreated());
        addToCart("bob", p.getId(), 1);
        mvc.perform(post("/api/shop/orders").with(member("bob"))).andExpect(status().isCreated());

        mvc.perform(get("/api/shop/orders/me").with(member("alice")))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** Deux membres achètent simultanément le dernier exemplaire : une seule commande doit aboutir. */
    @Test
    void lastItemCannotBeSoldTwice() throws Exception {
        Product p = product(1);
        addToCart("alice", p.getId(), 1);
        addToCart("bob", p.getId(), 1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> alice = () -> mvc.perform(post("/api/shop/orders").with(member("alice")))
                    .andReturn().getResponse().getStatus();
            Callable<Integer> bob = () -> mvc.perform(post("/api/shop/orders").with(member("bob")))
                    .andReturn().getResponse().getStatus();
            List<Integer> statuses = pool.invokeAll(List.of(alice, bob)).stream().map(ShopApiIT::statusOf).toList();

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(products.findById(p.getId()).orElseThrow().getStock()).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void subscriptionCanBeTakenThenRead() throws Exception {
        mvc.perform(get("/api/shop/subscriptions/me").with(member("carol"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/shop/subscriptions").with(member("carol")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"DIGITAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/shop/subscriptions/me").with(member("carol")))
                .andExpect(jsonPath("$.plan").value("DIGITAL"));
    }

    private static Integer statusOf(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
