package com.pme.shop.order;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Ligne figée au moment de l'achat (nom et prix historisés). */
@Embeddable
public class OrderLine {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "unit_price_cents", nullable = false)
    private long unitPriceCents;

    @Column(nullable = false)
    private int quantity;

    protected OrderLine() {
        // JPA
    }

    public OrderLine(UUID productId, String productName, long unitPriceCents, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.unitPriceCents = unitPriceCents;
        this.quantity = quantity;
    }

    public long totalCents() {
        return unitPriceCents * quantity;
    }

    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public long getUnitPriceCents() { return unitPriceCents; }
    public int getQuantity() { return quantity; }
}
