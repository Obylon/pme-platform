package com.pme.community.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pme.common.web.PageResponse;
import com.pme.community.api.ListingDtos.CreateListingRequest;
import com.pme.community.api.ListingDtos.ListingResponse;
import com.pme.community.api.ListingDtos.ModerationRequest;
import com.pme.community.api.ListingDtos.ModerationResponse;
import com.pme.community.domain.Category;
import com.pme.community.domain.Listing;
import com.pme.community.domain.ListingService;
import com.pme.community.domain.ListingType;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/community/listings")
public class ListingController {

    private final ListingService service;

    public ListingController(ListingService service) {
        this.service = service;
    }

    /** US-102 : consultation publique et filtrage. */
    @GetMapping
    public PageResponse<ListingResponse> search(@RequestParam(required = false) Category category,
                                                @RequestParam(required = false) ListingType type,
                                                @PageableDefault(size = 20, sort = "createdAt",
                                                        direction = Sort.Direction.DESC) Pageable pageable) {
        Pageable bounded = pageable.getPageSize() > 100
                ? org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), 100, pageable.getSort())
                : pageable;
        return PageResponse.of(service.search(category, type, bounded), ListingResponse::from);
    }

    @GetMapping("/{id}")
    public ListingResponse get(@PathVariable UUID id) {
        return ListingResponse.from(service.getPublished(id));
    }

    /** US-101 : publication par un membre authentifié. */
    @PostMapping
    @PreAuthorize("hasRole('member')")
    public ResponseEntity<ListingResponse> publish(@Valid @RequestBody CreateListingRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        Listing listing = service.publish(jwt.getSubject(), request.title(), request.description(),
                request.category(), request.type(), request.keywords());
        return ResponseEntity.created(URI.create("/api/community/listings/" + listing.getId()))
                .body(ListingResponse.from(listing));
    }

    /** US-103 : retrait par le propriétaire (contrôle de propriété dans le domaine). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('member')")
    public ResponseEntity<Void> withdraw(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.withdraw(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    /** US-104 : modération, réservée au rôle moderator. */
    @PostMapping("/{id}/moderation")
    @PreAuthorize("hasRole('moderator')")
    public ModerationResponse moderate(@PathVariable UUID id, @Valid @RequestBody ModerationRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return ModerationResponse.from(service.moderate(id, jwt.getSubject(), request.reason()));
    }
}
