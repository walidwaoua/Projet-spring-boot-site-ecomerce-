package com.ecommerce.notification.services;

import com.ecommerce.notification.config.RabbitMqNotificationConfig;
import com.ecommerce.notification.messaging.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class OrderNotificationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderNotificationListener.class);

    @RabbitListener(queues = RabbitMqNotificationConfig.ORDER_NOTIFICATION_QUEUE)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        LOGGER.info(
            "Notification client envoyee - orderId={}, userId={}, status={}, message={}",
            event.orderId(),
            event.userId(),
            event.status(),
            event.message()
        );
    }
}
