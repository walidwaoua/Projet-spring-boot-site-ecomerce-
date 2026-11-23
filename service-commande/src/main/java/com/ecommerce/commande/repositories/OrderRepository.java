package com.ecommerce.commande.repositories;

import com.ecommerce.commande.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    java.util.Optional<Order> findByIdempotencyKey(String idempotencyKey);
}
