package com.pme.shop.subscription;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Abonnement au fanzine : un seul par membre, prolongé à chaque renouvellement. */
@Entity
@Table(name = "subscription")
public class Subscription {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SubscriptionPlan plan;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Version
    private long version;

    protected Subscription() {
        // JPA
    }

    public static Subscription start(String userId, SubscriptionPlan plan, LocalDate today) {
        Subscription s = new Subscription();
        s.id = UUID.randomUUID();
        s.userId = userId;
        s.plan = plan;
        s.startDate = today;
        s.endDate = today.plusYears(1);
        return s;
    }

    /**
     * US-301 : un renouvellement anticipé prolonge d'un an à partir de la fin en cours ;
     * un abonnement expiré repart d'aujourd'hui.
     */
    public void renew(SubscriptionPlan newPlan, LocalDate today) {
        LocalDate base = endDate.isAfter(today) ? endDate : today;
        if (!endDate.isAfter(today)) {
            startDate = today;
        }
        endDate = base.plusYears(1);
        plan = newPlan;
    }

    public boolean isActive(LocalDate today) {
        return !endDate.isBefore(today);
    }

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public SubscriptionPlan getPlan() { return plan; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
}
