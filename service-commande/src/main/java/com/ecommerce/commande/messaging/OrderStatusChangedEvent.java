package com.ecommerce.commande.messaging;

import java.time.Instant;

public record OrderStatusChangedEvent(
    Long orderId,
    Long userId,
    String status,
    String message,
    Instant occurredAt
) {
}
