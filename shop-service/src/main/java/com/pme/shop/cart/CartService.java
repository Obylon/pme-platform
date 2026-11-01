package com.pme.shop.cart;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pme.common.web.ResourceNotFoundException;
import com.pme.shop.catalog.Product;
import com.pme.shop.catalog.ProductRepository;

@Service
public class CartService {

    public record CartLine(UUID productId, String name, long unitPriceCents, int quantity, long lineTotalCents) {
    }

    public record CartView(List<CartLine> lines, long totalCents) {
    }

    private final CartItemRepository items;
    private final ProductRepository products;

    public CartService(CartItemRepository items, ProductRepository products) {
        this.items = items;
        this.products = products;
    }

    @Transactional
    public CartView add(String userId, UUID productId, int quantity) {
        products.findById(productId).filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable"));
        items.findByUserIdAndProductId(userId, productId)
                .ifPresentOrElse(item -> item.add(quantity),
                        () -> items.save(new CartItem(userId, productId, quantity)));
        return view(userId);
    }

    @Transactional
    public CartView remove(String userId, UUID productId) {
        items.findByUserIdAndProductId(userId, productId).ifPresent(item -> items.delete(item));
        return view(userId);
    }

    @Transactional(readOnly = true)
    public CartView view(String userId) {
        List<CartLine> lines = items.findByUserId(userId).stream()
                .flatMap(item -> products.findById(item.getProductId())
                        .map(p -> new CartLine(p.getId(), p.getName(), p.getPriceCents(), item.getQuantity(),
                                p.getPriceCents() * item.getQuantity()))
                        .stream())
                .toList();
        return new CartView(lines, lines.stream().mapToLong(CartLine::lineTotalCents).sum());
    }
}
