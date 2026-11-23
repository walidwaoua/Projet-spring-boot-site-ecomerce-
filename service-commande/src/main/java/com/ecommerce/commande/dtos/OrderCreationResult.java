package com.ecommerce.commande.dtos;

public record OrderCreationResult(OrderResponse order, boolean replayed) {
}
