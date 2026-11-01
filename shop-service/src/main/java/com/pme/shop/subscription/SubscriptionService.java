package com.pme.shop.subscription;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pme.common.web.ResourceNotFoundException;
import com.pme.shop.payment.PaymentGateway;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final PaymentGateway payment;
    private final Clock clock;

    public SubscriptionService(SubscriptionRepository subscriptions, PaymentGateway payment, Clock clock) {
        this.subscriptions = subscriptions;
        this.payment = payment;
        this.clock = clock;
    }

    /** US-301 : souscription ou renouvellement, payé avant enregistrement. */
    @Transactional
    public Subscription subscribeOrRenew(String userId, SubscriptionPlan plan) {
        LocalDate today = LocalDate.now(clock);
        payment.charge(userId, plan.yearlyPriceCents(), "Abonnement fanzine " + plan);
        return subscriptions.findByUserId(userId)
                .map(existing -> {
                    existing.renew(plan, today);
                    return existing;
                })
                .orElseGet(() -> subscriptions.save(Subscription.start(userId, plan, today)));
    }

    @Transactional(readOnly = true)
    public Subscription mine(String userId) {
        return subscriptions.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun abonnement"));
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
