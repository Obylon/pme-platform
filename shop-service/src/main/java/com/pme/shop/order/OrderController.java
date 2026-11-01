package com.pme.shop.order;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pme.common.web.PageResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/shop/orders")
@PreAuthorize("hasRole('member')")
public class OrderController {

    public record OrderLineResponse(UUID productId, String productName, long unitPriceCents, int quantity) {
    }

    public record OrderResponse(UUID id, OrderStatus status, long totalCents, Instant createdAt,
                                List<OrderLineResponse> lines) {
        static OrderResponse from(CustomerOrder o) {
            return new OrderResponse(o.getId(), o.getStatus(), o.getTotalCents(), o.getCreatedAt(),
                    o.getLines().stream().map(l -> new OrderLineResponse(l.getProductId(), l.getProductName(),
                            l.getUnitPriceCents(), l.getQuantity())).toList());
        }
    }

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /** US-203 : passer commande à partir du panier. */
    @PostMapping
    public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal Jwt jwt) {
        CustomerOrder order = orders.checkout(jwt.getSubject());
        return ResponseEntity.created(URI.create("/api/shop/orders/" + order.getId())).body(OrderResponse.from(order));
    }

    /** US-204 : historique des commandes du membre connecté uniquement. */
    @GetMapping("/me")
    public PageResponse<OrderResponse> myOrders(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(orders.myOrders(jwt.getSubject(), PageRequest.of(page, size)), OrderResponse::from);
    }
}
