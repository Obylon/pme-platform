package com.pme.shop.subscription;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** US-301 / US-302 : abonnement au fanzine du membre connecté. */
@RestController
@RequestMapping("/api/shop/subscriptions")
@PreAuthorize("hasRole('member')")
public class SubscriptionController {

    public record SubscribeRequest(@NotNull SubscriptionPlan plan) {
    }

    public record SubscriptionResponse(SubscriptionPlan plan, LocalDate startDate, LocalDate endDate, String status) {
    }

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    public SubscriptionResponse subscribe(@Valid @RequestBody SubscribeRequest request, @AuthenticationPrincipal Jwt jwt) {
        return toResponse(service.subscribeOrRenew(jwt.getSubject(), request.plan()));
    }

    @GetMapping("/me")
    public SubscriptionResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return toResponse(service.mine(jwt.getSubject()));
    }

    private SubscriptionResponse toResponse(Subscription s) {
        return new SubscriptionResponse(s.getPlan(), s.getStartDate(), s.getEndDate(),
                s.isActive(service.today()) ? "ACTIVE" : "EXPIRED");
    }
}
