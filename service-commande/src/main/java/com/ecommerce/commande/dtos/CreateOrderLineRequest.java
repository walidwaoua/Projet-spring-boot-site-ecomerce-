package com.ecommerce.commande.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateOrderLineRequest(
    @NotNull @Positive Long productId,
    @NotNull @Positive Integer quantite,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal prixUnitaire
) {
}
