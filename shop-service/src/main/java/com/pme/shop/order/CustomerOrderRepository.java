package com.pme.shop.order;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    Page<CustomerOrder> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
