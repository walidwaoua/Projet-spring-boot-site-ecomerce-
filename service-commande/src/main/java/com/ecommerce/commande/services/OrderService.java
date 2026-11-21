package com.ecommerce.commande.services;

import com.ecommerce.commande.dtos.CreateOrderLineRequest;
import com.ecommerce.commande.dtos.CreateOrderRequest;
import com.ecommerce.commande.dtos.OrderLineResponse;
import com.ecommerce.commande.dtos.OrderResponse;
import com.ecommerce.commande.entities.Order;
import com.ecommerce.commande.entities.OrderLine;
import com.ecommerce.commande.enums.OrderStatus;
import com.ecommerce.commande.exceptions.InvalidOrderStateException;
import com.ecommerce.commande.exceptions.OrderNotFoundException;
import com.ecommerce.commande.repositories.OrderLineRepository;
import com.ecommerce.commande.repositories.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;

    public OrderService(OrderRepository orderRepository, OrderLineRepository orderLineRepository) {
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        List<Order> orders = orderRepository.findAll();
        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderLine>> linesByOrderId = orderLineRepository.findAllByOrderIdIn(orderIds)
            .stream()
            .collect(Collectors.groupingBy(OrderLine::getOrderId));

        return orders.stream()
            .map(order -> toResponse(order, linesByOrderId.getOrDefault(order.getId(), List.of())))
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long orderId) {
        Order order = getOrder(orderId);
        return toResponse(order, orderLineRepository.findAllByOrderId(orderId));
    }

    public OrderResponse create(CreateOrderRequest request) {
        BigDecimal total = request.lignes().stream()
            .map(this::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = orderRepository.save(
            new Order(request.userId(), OrderStatus.EN_ATTENTE_STOCK, total)
        );

        List<OrderLine> lines = request.lignes().stream()
            .map(line -> new OrderLine(order.getId(), line.productId(), line.quantite(), line.prixUnitaire()))
            .toList();
        List<OrderLine> savedLines = orderLineRepository.saveAll(lines);

        return toResponse(order, savedLines);
    }

    public OrderResponse confirm(Long orderId) {
        Order order = getOrder(orderId);
        if (order.getStatut() != OrderStatus.EN_ATTENTE_STOCK) {
            throw new InvalidOrderStateException(
                "Seule une commande en attente de stock peut être confirmée"
            );
        }

        order.setStatut(OrderStatus.CONFIRMEE);
        return toResponse(orderRepository.save(order), orderLineRepository.findAllByOrderId(orderId));
    }

    public OrderResponse cancel(Long orderId) {
        Order order = getOrder(orderId);
        if (order.getStatut() == OrderStatus.ANNULEE) {
            throw new InvalidOrderStateException("La commande est déjà annulée");
        }

        order.setStatut(OrderStatus.ANNULEE);
        return toResponse(orderRepository.save(order), orderLineRepository.findAllByOrderId(orderId));
    }

    private Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private BigDecimal lineTotal(CreateOrderLineRequest line) {
        return line.prixUnitaire().multiply(BigDecimal.valueOf(line.quantite()));
    }

    private OrderResponse toResponse(Order order, List<OrderLine> lines) {
        List<OrderLineResponse> lineResponses = lines.stream()
            .map(line -> new OrderLineResponse(
                line.getId(), line.getProductId(), line.getQuantite(), line.getPrixUnitaire()
            ))
            .toList();

        return new OrderResponse(
            order.getId(),
            order.getUserId(),
            order.getDate(),
            order.getStatut(),
            order.getMontantTotal(),
            lineResponses
        );
    }
}
