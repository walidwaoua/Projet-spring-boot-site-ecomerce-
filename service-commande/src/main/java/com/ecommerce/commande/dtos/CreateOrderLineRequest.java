package com.ecommerce.commande.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderLineRequest(
    @NotNull @Positive Long productId,
    @NotNull @Positive Integer quantite
) {
}
