package com.ecommerce.commande.dtos;

import java.math.BigDecimal;

public record OrderLineResponse(
    Long id,
    Long productId,
    Integer quantite,
    BigDecimal prixUnitaire
) {
}
