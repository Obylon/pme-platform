package com.pme.shop.catalog;

import java.util.UUID;

import com.pme.common.web.BusinessConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Produit vendu en ligne. Le stock est protégé par verrouillage optimiste (@Version) :
 * deux commandes simultanées sur le dernier exemplaire ne peuvent pas réussir toutes les deux.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String sku;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 30)
    private String category;

    /** Prix TTC en centimes d'euro (pas de flottant pour des montants). */
    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private boolean exclusive;

    @Column(nullable = false)
    private boolean active;

    @Version
    private long version;

    protected Product() {
        // JPA
    }

    public Product(UUID id, String sku, String name, String category, long priceCents, int stock,
                   boolean exclusive) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.priceCents = priceCents;
        this.stock = stock;
        this.exclusive = exclusive;
        this.active = true;
    }

    public void reserve(int quantity) {
        if (!active || quantity > stock) {
            throw new BusinessConflictException("Stock insuffisant pour « " + name + " »");
        }
        stock -= quantity;
    }

    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public long getPriceCents() { return priceCents; }
    public int getStock() { return stock; }
    public boolean isExclusive() { return exclusive; }
    public boolean isActive() { return active; }
}
