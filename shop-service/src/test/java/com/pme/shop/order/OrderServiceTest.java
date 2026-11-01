package com.pme.shop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pme.common.web.BusinessConflictException;
import com.pme.common.web.InvalidRequestException;
import com.pme.shop.cart.CartItem;
import com.pme.shop.cart.CartItemRepository;
import com.pme.shop.catalog.Product;
import com.pme.shop.catalog.ProductRepository;
import com.pme.shop.payment.PaymentGateway;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    CartItemRepository cartItems;
    @Mock
    ProductRepository products;
    @Mock
    CustomerOrderRepository orders;
    @Mock
    PaymentGateway payment;

    OrderService service;
    Product orc;

    @BeforeEach
    void setUp() {
        service = new OrderService(cartItems, products, orders, payment,
                Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC));
        orc = new Product(UUID.randomUUID(), "FIG-ORC", "Figurine Orc", "FIGURINE", 5990, 5, true);
    }

    @Test
    void checkoutDecrementsStockChargesAndEmptiesCart() {
        List<CartItem> cart = List.of(new CartItem("alice", orc.getId(), 2));
        when(cartItems.findByUserId("alice")).thenReturn(cart);
        when(products.findById(orc.getId())).thenReturn(Optional.of(orc));
        when(payment.charge(eq("alice"), eq(11980L), anyString())).thenReturn("SIM-1");
        when(orders.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerOrder order = service.checkout("alice");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getTotalCents()).isEqualTo(11980L);
        assertThat(order.getPaymentReference()).isEqualTo("SIM-1");
        assertThat(orc.getStock()).isEqualTo(3);
        verify(cartItems).deleteAll(cart);
    }

    @Test
    void insufficientStockIsAConflictAndNothingIsCharged() {
        when(cartItems.findByUserId("alice")).thenReturn(List.of(new CartItem("alice", orc.getId(), 6)));
        when(products.findById(orc.getId())).thenReturn(Optional.of(orc));

        assertThatThrownBy(() -> service.checkout("alice")).isInstanceOf(BusinessConflictException.class);
        verify(payment, never()).charge(anyString(), anyLong(), anyString());
        assertThat(orc.getStock()).isEqualTo(5);
    }

    @Test
    void emptyCartIsRejected() {
        when(cartItems.findByUserId("alice")).thenReturn(List.of());

        assertThatThrownBy(() -> service.checkout("alice")).isInstanceOf(InvalidRequestException.class);
    }
}
