package com.pme.shop.payment;

/**
 * Port vers le prestataire de paiement (PSP certifié PCI-DSS, ex. Stripe, Adyen, Payplug).
 * La plateforme ne manipule jamais de numéro de carte : le front obtient un jeton de paiement du PSP.
 */
public interface PaymentGateway {

    /** @return la référence de transaction du prestataire */
    String charge(String customerId, long amountCents, String description);
}
