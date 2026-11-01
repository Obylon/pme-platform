package com.pme.notification.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pme.common.web.PageResponse;
import com.pme.notification.domain.Notification;
import com.pme.notification.domain.NotificationPreference;
import com.pme.notification.domain.NotificationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasRole('member')")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    public record PreferencesRequest(
            @Size(max = NotificationPreference.MAX_ITEMS) Set<@Size(max = 20) String> categories,
            @Size(max = NotificationPreference.MAX_ITEMS) Set<@Size(max = 30) String> keywords,
            @NotNull Boolean enabled) {
    }

    public record PreferencesResponse(Set<String> categories, Set<String> keywords, boolean enabled) {
        static PreferencesResponse from(NotificationPreference p) {
            return new PreferencesResponse(p.getCategories(), p.getKeywords(), p.isEnabled());
        }
    }

    public record NotificationResponse(UUID id, UUID listingId, String message, Instant createdAt, boolean read) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getListingId(), n.getMessage(), n.getCreatedAt(), n.isRead());
        }
    }

    /** US-105 : centres d'intérêt et activation des notifications. */
    @PutMapping("/preferences/me")
    public PreferencesResponse updatePreferences(@Valid @RequestBody PreferencesRequest request,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return PreferencesResponse.from(service.updatePreferences(jwt.getSubject(), request.categories(),
                request.keywords(), request.enabled()));
    }

    @GetMapping("/preferences/me")
    public PreferencesResponse getPreferences(@AuthenticationPrincipal Jwt jwt) {
        return PreferencesResponse.from(service.getPreferences(jwt.getSubject()));
    }

    /** US-106 : notifications du membre connecté uniquement (identifiant issu du jeton, jamais d'un paramètre). */
    @GetMapping("/me")
    public PageResponse<NotificationResponse> myNotifications(@AuthenticationPrincipal Jwt jwt,
                                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(service.myNotifications(jwt.getSubject(), PageRequest.of(page, size)),
                NotificationResponse::from);
    }
}
