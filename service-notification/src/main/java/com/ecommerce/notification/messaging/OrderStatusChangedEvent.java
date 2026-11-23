package com.ecommerce.notification.messaging;

import java.time.Instant;

public record OrderStatusChangedEvent(
    Long orderId,
    Long userId,
    String status,
    String message,
    Instant occurredAt
) {
}
