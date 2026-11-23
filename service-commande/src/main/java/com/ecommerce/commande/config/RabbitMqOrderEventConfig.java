package com.ecommerce.commande.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqOrderEventConfig {

    public static final String ORDER_EVENTS_EXCHANGE = "order.events.exchange";
    public static final String ORDER_CONFIRMED_ROUTING_KEY = "order.confirmed";
    public static final String ORDER_CANCELLED_ROUTING_KEY = "order.cancelled";
    public static final String ORDER_NOTIFICATION_QUEUE = "order.notification.queue";

    @Bean
    TopicExchange orderEventsExchange() {
        return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    Queue orderNotificationQueue() {
        return new Queue(ORDER_NOTIFICATION_QUEUE, true);
    }

    @Bean
    Binding orderConfirmedBinding(Queue orderNotificationQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(orderNotificationQueue)
            .to(orderEventsExchange)
            .with(ORDER_CONFIRMED_ROUTING_KEY);
    }

    @Bean
    Binding orderCancelledBinding(Queue orderNotificationQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(orderNotificationQueue)
            .to(orderEventsExchange)
            .with(ORDER_CANCELLED_ROUTING_KEY);
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
