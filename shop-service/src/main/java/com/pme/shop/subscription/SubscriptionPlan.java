package com.pme.shop.subscription;

/** Formules annuelles du fanzine (4 numéros par an). Prix TTC en centimes. */
public enum SubscriptionPlan {
    PAPER(3200),
    DIGITAL(2000),
    BOTH(4200);

    private final long yearlyPriceCents;

    SubscriptionPlan(long yearlyPriceCents) {
        this.yearlyPriceCents = yearlyPriceCents;
    }

    public long yearlyPriceCents() {
        return yearlyPriceCents;
    }
}
