package com.pme.notification.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Notification d'une annonce correspondant aux centres d'intérêt d'un membre. */
@Entity
@Table(name = "notification",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_user_listing", columnNames = {"user_id", "listing_id"}))
public class Notification {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(nullable = false, length = 200)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_flag", nullable = false)
    private boolean read;

    protected Notification() {
        // JPA
    }

    public Notification(String userId, UUID listingId, String message, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.listingId = listingId;
        this.message = message.length() > 200 ? message.substring(0, 200) : message;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public UUID getListingId() { return listingId; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isRead() { return read; }
}
