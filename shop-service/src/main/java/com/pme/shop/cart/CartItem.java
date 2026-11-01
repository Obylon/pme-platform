package com.pme.shop.cart;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "cart_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_cart_user_product", columnNames = {"user_id", "product_id"}))
public class CartItem {

    public static final int MAX_QUANTITY = 10;

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    protected CartItem() {
        // JPA
    }

    public CartItem(String userId, UUID productId, int quantity) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
    }

    /** Ajoute une quantité, plafonnée pour limiter la réservation abusive de stock. */
    public void add(int extra) {
        this.quantity = Math.min(MAX_QUANTITY, this.quantity + extra);
    }

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
}
