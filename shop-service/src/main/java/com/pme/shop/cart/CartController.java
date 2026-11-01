package com.pme.shop.cart;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pme.shop.cart.CartService.CartView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** US-202 : panier du membre connecté (identifiant issu du jeton). */
@RestController
@RequestMapping("/api/shop/cart")
@PreAuthorize("hasRole('member')")
public class CartController {

    public record AddItemRequest(@NotNull UUID productId, @Min(1) @Max(CartItem.MAX_QUANTITY) int quantity) {
    }

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    public CartView view(@AuthenticationPrincipal Jwt jwt) {
        return cart.view(jwt.getSubject());
    }

    @PostMapping("/items")
    public CartView add(@Valid @RequestBody AddItemRequest request, @AuthenticationPrincipal Jwt jwt) {
        return cart.add(jwt.getSubject(), request.productId(), request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartView remove(@PathVariable UUID productId, @AuthenticationPrincipal Jwt jwt) {
        return cart.remove(jwt.getSubject(), productId);
    }
}
