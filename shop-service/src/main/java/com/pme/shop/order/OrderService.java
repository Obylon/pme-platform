package com.pme.shop.order;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pme.common.web.InvalidRequestException;
import com.pme.common.web.ResourceNotFoundException;
import com.pme.shop.cart.CartItem;
import com.pme.shop.cart.CartItemRepository;
import com.pme.shop.catalog.Product;
import com.pme.shop.catalog.ProductRepository;
import com.pme.shop.payment.PaymentGateway;

@Service
public class OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class);

    private final CartItemRepository cartItems;
    private final ProductRepository products;
    private final CustomerOrderRepository orders;
    private final PaymentGateway payment;
    private final Clock clock;

    public OrderService(CartItemRepository cartItems, ProductRepository products, CustomerOrderRepository orders,
                        PaymentGateway payment, Clock clock) {
        this.cartItems = cartItems;
        this.products = products;
        this.orders = orders;
        this.payment = payment;
        this.clock = clock;
    }

    /**
     * US-203 : transforme le panier en commande, dans une seule transaction.
     * Le stock de chaque produit est vérifié puis décrémenté ; si une ligne échoue
     * (stock insuffisant, modification concurrente détectée par @Version), tout est annulé.
     */
    @Transactional
    public CustomerOrder checkout(String userId) {
        List<CartItem> items = cartItems.findByUserId(userId);
        if (items.isEmpty()) {
            throw new InvalidRequestException("Le panier est vide");
        }
        List<OrderLine> lines = new ArrayList<>();
        for (CartItem item : items) {
            Product product = products.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable"));
            product.reserve(item.getQuantity());
            lines.add(new OrderLine(product.getId(), product.getName(), product.getPriceCents(), item.getQuantity()));
        }
        // Écrit les décréments maintenant : un conflit concurrent (@Version) est détecté AVANT le paiement.
        products.flush();
        long total = lines.stream().mapToLong(OrderLine::totalCents).sum();
        String paymentReference = payment.charge(userId, total, "Commande petitemaisonepouvante.com");
        CustomerOrder order = orders.save(CustomerOrder.confirmed(userId, lines, paymentReference, Instant.now(clock)));
        cartItems.deleteAll(items);
        LOG.info("Commande confirmée id={} lignes={} total={}", order.getId(), lines.size(), total);
        return order;
    }

    @Transactional(readOnly = true)
    public Page<CustomerOrder> myOrders(String userId, Pageable pageable) {
        return orders.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
}
