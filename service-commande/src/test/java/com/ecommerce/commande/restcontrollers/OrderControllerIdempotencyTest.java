package com.ecommerce.commande.restcontrollers;

import com.ecommerce.commande.dtos.OrderCreationResult;
import com.ecommerce.commande.dtos.OrderResponse;
import com.ecommerce.commande.dtos.CreateOrderRequest;
import com.ecommerce.commande.enums.OrderStatus;
import com.ecommerce.commande.exceptions.GlobalExceptionHandler;
import com.ecommerce.commande.exceptions.IdempotencyConflictException;
import com.ecommerce.commande.services.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderControllerIdempotencyTest {
    private StubOrderService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = new StubOrderService();
        mvc = MockMvcBuilders.standaloneSetup(new OrderController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void firstCreateReturnsCreatedAndReplayHeaderFalse() throws Exception {
        service.result = new OrderCreationResult(order(), false);

        mvc.perform(post("/orders").header("Idempotency-Key", "client-key")
                .contentType("application/json")
                .content("{\"userId\":4,\"lignes\":[{\"productId\":9,\"quantite\":2}]}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotency-Replayed", "false"))
            .andExpect(jsonPath("$.id").value(12));
    }

    @Test
    void replayReturnsOkAndConflictUsesCentralAdvice() throws Exception {
        service.result = new OrderCreationResult(order(), true);
        mvc.perform(post("/orders").header("Idempotency-Key", "client-key")
                .contentType("application/json")
                .content("{\"userId\":4,\"lignes\":[{\"productId\":9,\"quantite\":2}]}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Idempotency-Replayed", "true"));

        service.failure = new IdempotencyConflictException("key reused");
        mvc.perform(post("/orders").header("Idempotency-Key", "client-key")
                .contentType("application/json")
                .content("{\"userId\":4,\"lignes\":[{\"productId\":9,\"quantite\":3}]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("key reused"));
    }

    @Test
    void missingKeyIsRejectedAsBadRequest() throws Exception {
        mvc.perform(post("/orders").contentType("application/json")
                .content("{\"userId\":4,\"lignes\":[{\"productId\":9,\"quantite\":2}]}"))
            .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertFalse(service.called);
    }

    private OrderResponse order() {
        return new OrderResponse(12L, 4L, null, OrderStatus.CONFIRMEE, new BigDecimal("25.00"), List.of());
    }

    private static class StubOrderService extends OrderService {
        private OrderCreationResult result;
        private RuntimeException failure;
        private boolean called;

        StubOrderService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public OrderCreationResult create(CreateOrderRequest request, String idempotencyKey) {
            called = true;
            if (failure != null) throw failure;
            return result;
        }
    }
}
