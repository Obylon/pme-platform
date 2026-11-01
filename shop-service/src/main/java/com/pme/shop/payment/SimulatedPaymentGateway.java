package com.pme.shop.payment;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Paiement simulé pour le POC : toujours accepté. Remplacé par l'adaptateur du PSP en production. */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    @Override
    public String charge(String customerId, long amountCents, String description) {
        String reference = "SIM-" + UUID.randomUUID();
        LOG.info("Paiement simulé accepté montant={} ref={}", amountCents, reference);
        return reference;
    }
}
