package com.pme.shop.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pme.shop.payment.PaymentGateway;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Mock
    SubscriptionRepository repository;
    @Mock
    PaymentGateway payment;

    SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(repository, payment,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @Test
    void firstSubscriptionLastsOneYear() {
        when(repository.findByUserId("alice")).thenReturn(Optional.empty());
        when(repository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        Subscription s = service.subscribeOrRenew("alice", SubscriptionPlan.DIGITAL);

        assertThat(s.getStartDate()).isEqualTo(TODAY);
        assertThat(s.getEndDate()).isEqualTo(LocalDate.of(2027, 11, 1));
        verify(payment).charge(eq("alice"), eq(2000L), anyString());
    }

    @Test
    void earlyRenewalExtendsFromCurrentEndDate() {
        // Abonnement souscrit le 2026-03-01, actif jusqu'au 2027-03-01
        Subscription active = Subscription.start("alice", SubscriptionPlan.PAPER, LocalDate.of(2026, 3, 1));
        when(repository.findByUserId("alice")).thenReturn(Optional.of(active));

        Subscription s = service.subscribeOrRenew("alice", SubscriptionPlan.BOTH);

        assertThat(s.getEndDate()).isEqualTo(LocalDate.of(2028, 3, 1));
        assertThat(s.getPlan()).isEqualTo(SubscriptionPlan.BOTH);
        assertThat(s.isActive(TODAY)).isTrue();
    }

    @Test
    void expiredSubscriptionRestartsFromToday() {
        Subscription expired = Subscription.start("alice", SubscriptionPlan.PAPER, LocalDate.of(2024, 1, 1));
        when(repository.findByUserId("alice")).thenReturn(Optional.of(expired));
        assertThat(expired.isActive(TODAY)).isFalse();

        Subscription s = service.subscribeOrRenew("alice", SubscriptionPlan.PAPER);

        assertThat(s.getStartDate()).isEqualTo(TODAY);
        assertThat(s.getEndDate()).isEqualTo(LocalDate.of(2027, 11, 1));
    }
}
