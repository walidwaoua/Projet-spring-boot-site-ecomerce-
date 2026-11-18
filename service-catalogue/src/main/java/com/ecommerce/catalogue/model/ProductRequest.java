package com.ecommerce.catalogue.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ProductRequest(
    @NotBlank String nom,
    @NotNull @Positive BigDecimal prix,
    String description,
    String categorie,
    @NotNull ProductStatus statut
) {}
