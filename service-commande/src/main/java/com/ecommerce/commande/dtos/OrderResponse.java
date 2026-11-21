package com.ecommerce.commande.dtos;

import com.ecommerce.commande.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
    Long id,
    Long userId,
    LocalDateTime date,
    OrderStatus statut,
    BigDecimal montantTotal,
    List<OrderLineResponse> lignes
) {
}
