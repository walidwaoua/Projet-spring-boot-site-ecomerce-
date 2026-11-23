package com.ecommerce.stock.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public record StockQuantity(@NotNull @Positive Long productId, @NotNull @Positive Integer quantite) {}
