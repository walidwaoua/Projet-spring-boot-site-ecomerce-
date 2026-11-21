package com.ecommerce.commande.repositories;

import com.ecommerce.commande.entities.OrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {
    List<OrderLine> findAllByOrderId(Long orderId);
    List<OrderLine> findAllByOrderIdIn(List<Long> orderIds);
}
