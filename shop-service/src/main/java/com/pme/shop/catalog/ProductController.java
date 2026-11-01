package com.pme.shop.catalog;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pme.common.web.PageResponse;
import com.pme.common.web.ResourceNotFoundException;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** US-201 : catalogue public. */
@RestController
@RequestMapping("/api/shop/products")
public class ProductController {

    public record ProductResponse(UUID id, String sku, String name, String category, long priceCents,
                                  boolean available, int stock, boolean exclusive) {
        static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getCategory(), p.getPriceCents(),
                    p.getStock() > 0, p.getStock(), p.isExclusive());
        }
    }

    private final ProductRepository products;

    public ProductController(ProductRepository products) {
        this.products = products;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(@RequestParam(required = false) String category,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name"));
        var result = category == null
                ? products.findByActiveTrue(pageable)
                : products.findByActiveTrueAndCategory(category.toUpperCase(java.util.Locale.ROOT), pageable);
        return PageResponse.of(result, ProductResponse::from);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ProductResponse get(@PathVariable UUID id) {
        return products.findById(id).filter(Product::isActive).map(ProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable"));
    }
}
