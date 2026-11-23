package com.ecommerce.stock.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
public record StockRequest(@NotNull @Positive Long productId, @NotNull @PositiveOrZero Integer quantite) {}
