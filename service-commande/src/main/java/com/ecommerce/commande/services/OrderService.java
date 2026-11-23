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
import com.ecommerce.commande.messaging.OrderEventPublisher;
import com.ecommerce.commande.repositories.OrderLineRepository;
import com.ecommerce.commande.repositories.OrderRepository;
import com.ecommerce.commande.clients.*;
import com.ecommerce.commande.dtos.EnrichedOrderResponse;
import com.ecommerce.commande.dtos.OrderCreationResult;
import com.ecommerce.commande.exceptions.ExternalServiceException;
import com.ecommerce.commande.exceptions.IdempotencyConflictException;
import com.ecommerce.commande.exceptions.InvalidReferenceException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final UserClient users;
    private final ProductClient products;
    private final StockClient stock;
    private final OrderEventPublisher orderEventPublisher;
    private final FeignResilienceExecutor externalCalls;

    public OrderService(OrderRepository orderRepository, OrderLineRepository orderLineRepository, UserClient users, ProductClient products, StockClient stock, OrderEventPublisher orderEventPublisher, FeignResilienceExecutor externalCalls) {
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.users = users; this.products = products; this.stock = stock;
        this.orderEventPublisher = orderEventPublisher;
        this.externalCalls = externalCalls;
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

    public OrderCreationResult create(CreateOrderRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("L'en-tête Idempotency-Key est obligatoire et limité à 128 caractères");
        }
        String requestHash = fingerprint(request);
        var existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOrder.isPresent()) {
            Order existing = existingOrder.get();
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new IdempotencyConflictException("Cette clé Idempotency-Key est déjà associée à une autre demande");
            }
            return new OrderCreationResult(toResponse(existing, orderLineRepository.findAllByOrderId(existing.getId())), true);
        }
        validateUser(request.userId());
        Map<Long, ProductClient.ProductInfo> productData = new java.util.LinkedHashMap<>();
        for (CreateOrderLineRequest line : request.lignes()) productData.computeIfAbsent(line.productId(), this::getAvailableProduct);
        BigDecimal total = request.lignes().stream().map(line -> productData.get(line.productId()).prix().multiply(BigDecimal.valueOf(line.quantite()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        Order order;
        try {
            order = orderRepository.save(new Order(request.userId(), OrderStatus.EN_ATTENTE_STOCK, total, idempotencyKey, requestHash));
        } catch (DataIntegrityViolationException concurrentReplay) {
            throw new IdempotencyConflictException("Une demande avec cette Idempotency-Key est déjà en cours; rejouez la même demande après sa réponse");
        }
        List<OrderLine> lines = request.lignes().stream().map(line -> new OrderLine(order.getId(), line.productId(), line.quantite(), productData.get(line.productId()).prix())).toList();
        List<OrderLine> savedLines = orderLineRepository.saveAll(lines);
        List<StockClient.StockQuantity> quantities = aggregateStock(lines);
        boolean reserved = false;
        try {
            stock.reserve(quantities); reserved = true;
            order.setStatut(OrderStatus.CONFIRMEE);
            Order savedOrder = orderRepository.save(order);
            orderEventPublisher.publishOrderConfirmed(savedOrder);
            return new OrderCreationResult(toResponse(savedOrder, savedLines), false);
        } catch (RuntimeException e) {
            if (reserved) try { stock.release(quantities); } catch (RuntimeException compensation) { e.addSuppressed(compensation); }
            throw e;
        }
    }

    public OrderResponse confirm(Long orderId) {
        Order order = getOrder(orderId);
        if (order.getStatut() != OrderStatus.EN_ATTENTE_STOCK) {
            throw new InvalidOrderStateException(
                "Seule une commande en attente de stock peut être confirmée"
            );
        }

        List<OrderLine> lines = orderLineRepository.findAllByOrderId(orderId);
        stock.reserve(aggregateStock(lines));
        order.setStatut(OrderStatus.CONFIRMEE);
        Order savedOrder = orderRepository.save(order);
        orderEventPublisher.publishOrderConfirmed(savedOrder);
        return toResponse(savedOrder, lines);
    }

    public OrderResponse cancel(Long orderId) {
        Order order = getOrder(orderId);
        if (order.getStatut() == OrderStatus.ANNULEE) {
            throw new InvalidOrderStateException("La commande est déjà annulée");
        }

        if (order.getStatut() == OrderStatus.CONFIRMEE) stock.release(aggregateStock(orderLineRepository.findAllByOrderId(orderId)));
        order.setStatut(OrderStatus.ANNULEE);
        Order savedOrder = orderRepository.save(order);
        orderEventPublisher.publishOrderCancelled(savedOrder);
        return toResponse(savedOrder, orderLineRepository.findAllByOrderId(orderId));
    }

    public OrderResponse updateUser(Long orderId, Long userId) {
        Order order = getOrder(orderId); validateUser(userId); order.setUserId(userId);
        return toResponse(orderRepository.save(order), orderLineRepository.findAllByOrderId(orderId));
    }

    public void delete(Long orderId) {
        Order order = getOrder(orderId);
        List<OrderLine> lines = orderLineRepository.findAllByOrderId(orderId);
        if (order.getStatut() == OrderStatus.CONFIRMEE) stock.release(aggregateStock(lines));
        orderLineRepository.deleteAllByOrderId(orderId); orderRepository.delete(order);
    }

    @Transactional(readOnly = true)
    public List<OrderLineResponse> findLines(Long orderId) {
        getOrder(orderId); return orderLineRepository.findAllByOrderId(orderId).stream()
            .map(line -> new OrderLineResponse(line.getId(), line.getProductId(), line.getQuantite(), line.getPrixUnitaire())).toList();
    }

    public OrderLineResponse addLine(Long orderId, com.ecommerce.commande.dtos.OrderLineRequest request) {
        Order order = getOrder(orderId); ensureConfirmed(order);
        ProductClient.ProductInfo product = getAvailableProduct(request.productId());
        StockClient.StockQuantity reservation = new StockClient.StockQuantity(request.productId(), request.quantite());
        stock.reserve(List.of(reservation));
        OrderLine line;
        try { line = orderLineRepository.save(new OrderLine(orderId, request.productId(), request.quantite(), product.prix())); }
        catch (RuntimeException failure) { try { stock.release(List.of(reservation)); } catch (RuntimeException compensation) { failure.addSuppressed(compensation); } throw failure; }
        recalculateTotal(order); return new OrderLineResponse(line.getId(), line.getProductId(), line.getQuantite(), line.getPrixUnitaire());
    }

    public OrderLineResponse updateLine(Long orderId, Long lineId, com.ecommerce.commande.dtos.OrderLineRequest request) {
        Order order = getOrder(orderId); ensureConfirmed(order); OrderLine line = getLine(orderId, lineId);
        ProductClient.ProductInfo product = getAvailableProduct(request.productId());
        int reserve = Objects.equals(line.getProductId(), request.productId()) ? Math.max(0, request.quantite() - line.getQuantite()) : request.quantite();
        if (reserve > 0) stock.reserve(List.of(new StockClient.StockQuantity(request.productId(), reserve)));
        try {
            int release = Objects.equals(line.getProductId(), request.productId()) ? Math.max(0, line.getQuantite() - request.quantite()) : line.getQuantite();
            if (release > 0) stock.release(List.of(new StockClient.StockQuantity(line.getProductId(), release)));
        } catch (RuntimeException failure) {
            if (reserve > 0) try { stock.release(List.of(new StockClient.StockQuantity(request.productId(), reserve))); } catch (RuntimeException compensation) { failure.addSuppressed(compensation); }
            throw failure;
        }
        line.setProductId(request.productId()); line.setQuantite(request.quantite()); line.setPrixUnitaire(product.prix());
        OrderLine saved = orderLineRepository.save(line); recalculateTotal(order);
        return new OrderLineResponse(saved.getId(), saved.getProductId(), saved.getQuantite(), saved.getPrixUnitaire());
    }

    public void deleteLine(Long orderId, Long lineId) {
        Order order = getOrder(orderId); ensureConfirmed(order); OrderLine line = getLine(orderId, lineId);
        stock.release(List.of(new StockClient.StockQuantity(line.getProductId(), line.getQuantite())));
        orderLineRepository.delete(line); recalculateTotal(order);
    }

    private void ensureConfirmed(Order order) { if (order.getStatut() != OrderStatus.CONFIRMEE) throw new InvalidOrderStateException("Les lignes ne peuvent être modifiées que sur une commande confirmée"); }
    private OrderLine getLine(Long orderId, Long lineId) { return orderLineRepository.findById(lineId).filter(l -> l.getOrderId().equals(orderId)).orElseThrow(() -> new OrderNotFoundException(lineId)); }
    private void recalculateTotal(Order order) {
        BigDecimal total = orderLineRepository.findAllByOrderId(order.getId()).stream().map(l -> l.getPrixUnitaire().multiply(BigDecimal.valueOf(l.getQuantite()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setMontantTotal(total); orderRepository.save(order);
    }

    private Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public EnrichedOrderResponse findEnrichedById(Long orderId) {
        Order order = getOrder(orderId); List<OrderLine> lines = orderLineRepository.findAllByOrderId(orderId);
        UserClient.UserInfo user = call("utilisateurs", () -> users.findById(order.getUserId()));
        List<EnrichedOrderResponse.EnrichedLine> enriched = lines.stream().map(line -> {
            ProductClient.ProductInfo product = call("catalogue", () -> products.findById(line.getProductId()));
            Integer available; try { available = stock.findByProduct(line.getProductId()).quantite(); } catch (FeignException.NotFound e) { available = 0; }
            return new EnrichedOrderResponse.EnrichedLine(line.getId(), line.getProductId(), product.nom(), line.getQuantite(), available, line.getPrixUnitaire());
        }).toList();
        return new EnrichedOrderResponse(order.getId(), new EnrichedOrderResponse.UserSummary(user.id(), user.nom(), user.email(), user.role(), user.actif()), order.getDate(), order.getStatut(), order.getMontantTotal(), enriched);
    }

    private List<StockClient.StockQuantity> aggregateStock(List<OrderLine> lines) {
        return lines.stream().collect(Collectors.groupingBy(OrderLine::getProductId, Collectors.summingInt(OrderLine::getQuantite)))
            .entrySet().stream().map(e -> new StockClient.StockQuantity(e.getKey(), e.getValue())).toList();
    }
    private void validateUser(Long id) {
        UserClient.UserInfo user = call("utilisateurs", () -> users.findById(id));
        if (!user.actif()) throw new InvalidReferenceException("L'utilisateur est inactif : " + id);
    }
    private ProductClient.ProductInfo getAvailableProduct(Long id) {
        ProductClient.ProductInfo product = call("catalogue", () -> products.findById(id));
        if (!"DISPONIBLE".equals(product.statut()) || product.prix() == null || product.prix().signum() <= 0) throw new InvalidReferenceException("Produit indisponible ou prix invalide : " + id);
        return product;
    }
    private <T> T call(String service, java.util.function.Supplier<T> call) {
        try {
            return externalCalls.execute(service, call);
        } catch (FeignException.NotFound e) { throw new InvalidReferenceException("Référence externe introuvable"); }
        catch (FeignException | CallNotPermittedException e) { throw new ExternalServiceException("Un service externe est indisponible"); }
    }

    private String fingerprint(CreateOrderRequest request) {
        String canonical = request.userId() + ":" + request.lignes().stream()
            .map(line -> line.productId() + "x" + line.quantite()).collect(Collectors.joining(","));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
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
