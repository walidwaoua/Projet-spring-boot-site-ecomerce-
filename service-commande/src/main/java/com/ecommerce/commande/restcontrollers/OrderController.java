package com.ecommerce.commande.restcontrollers;

import com.ecommerce.commande.dtos.CreateOrderRequest;
import com.ecommerce.commande.dtos.OrderResponse;
import com.ecommerce.commande.dtos.EnrichedOrderResponse;
import com.ecommerce.commande.dtos.OrderLineRequest;
import com.ecommerce.commande.dtos.OrderLineResponse;
import com.ecommerce.commande.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> findAll() {
        return orderService.findAll();
    }

    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable Long id) {
        return orderService.findById(id);
    }

    @GetMapping("/{id}/details")
    public EnrichedOrderResponse findEnrichedById(@PathVariable Long id) { return orderService.findEnrichedById(id); }

    @GetMapping("/{id}/lines")
    public List<OrderLineResponse> findLines(@PathVariable Long id) { return orderService.findLines(id); }

    @PostMapping("/{id}/lines") @ResponseStatus(HttpStatus.CREATED)
    public OrderLineResponse addLine(@PathVariable Long id, @Valid @RequestBody OrderLineRequest request) { return orderService.addLine(id, request); }

    @PutMapping("/{id}/lines/{lineId}")
    public OrderLineResponse updateLine(@PathVariable Long id, @PathVariable Long lineId, @Valid @RequestBody OrderLineRequest request) { return orderService.updateLine(id, lineId, request); }

    @DeleteMapping("/{id}/lines/{lineId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLine(@PathVariable Long id, @PathVariable Long lineId) { orderService.deleteLine(id, lineId); }

    @PutMapping("/{id}")
    public OrderResponse updateUser(@PathVariable Long id, @RequestParam Long userId) { return orderService.updateUser(id, userId); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { orderService.delete(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }

    @PostMapping("/{id}/confirm")
    public OrderResponse confirm(@PathVariable Long id) {
        return orderService.confirm(id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id) {
        return orderService.cancel(id);
    }
}
