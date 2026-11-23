package com.ecommerce.commande.messaging;

import com.ecommerce.commande.config.RabbitMqOrderEventConfig;
import com.ecommerce.commande.entities.Order;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

@Component
public class OrderEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishOrderConfirmed(Order order) {
        publishAfterCommit(
            RabbitMqOrderEventConfig.ORDER_CONFIRMED_ROUTING_KEY,
            new OrderStatusChangedEvent(
                order.getId(),
                order.getUserId(),
                order.getStatut().name(),
                "Votre commande a ete confirmee.",
                Instant.now()
            )
        );
    }

    public void publishOrderCancelled(Order order) {
        publishAfterCommit(
            RabbitMqOrderEventConfig.ORDER_CANCELLED_ROUTING_KEY,
            new OrderStatusChangedEvent(
                order.getId(),
                order.getUserId(),
                order.getStatut().name(),
                "Votre commande a ete annulee.",
                Instant.now()
            )
        );
    }

    private void publishAfterCommit(String routingKey, OrderStatusChangedEvent event) {
        Runnable publishAction = () -> rabbitTemplate.convertAndSend(
            RabbitMqOrderEventConfig.ORDER_EVENTS_EXCHANGE,
            routingKey,
            event
        );
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishAction.run();
            }
        });
    }
}
