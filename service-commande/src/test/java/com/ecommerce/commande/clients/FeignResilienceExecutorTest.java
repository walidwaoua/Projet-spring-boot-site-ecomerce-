package com.ecommerce.commande.clients;

import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FeignResilienceExecutorTest {
    @Test
    void retriesTransientFeignFailureAndThenSucceeds() {
        var executor = executor(2, 5, 3);
        var attempts = new AtomicInteger();

        String result = executor.execute("retry-demo", () -> {
            if (attempts.incrementAndGet() == 1) throw unavailable();
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void opensCircuitAfterRepeatedRemoteFailuresAndRejectsTheNextCall() {
        var executor = executor(1, 3, 3);
        var attempts = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            assertThrows(FeignException.class,
                () -> executor.execute("circuit-demo", () -> { attempts.incrementAndGet(); throw unavailable(); }));
        }
        assertThrows(CallNotPermittedException.class,
            () -> executor.execute("circuit-demo", () -> { attempts.incrementAndGet(); return "unexpected"; }));
        assertEquals(3, attempts.get());
    }

    @Test
    void clientErrorsDoNotTripAvailabilityCircuit() {
        var executor = executor(1, 3, 3);
        for (int i = 0; i < 4; i++) {
            assertThrows(FeignException.class,
                () -> executor.execute("not-found-demo", () -> { throw notFound(); }));
        }
        assertEquals("recovered", executor.execute("not-found-demo", () -> "recovered"));
    }

    private FeignResilienceExecutor executor(int maxAttempts, int window, int minimumCalls) {
        return new FeignResilienceExecutor(maxAttempts, java.time.Duration.ZERO, window, minimumCalls,
            50, java.time.Duration.ofSeconds(10));
    }

    private FeignException unavailable() {
        return feignException(503, "unavailable");
    }

    private FeignException notFound() {
        return feignException(404, "not found");
    }

    private FeignException feignException(int status, String reason) {
        Request request = Request.create(Request.HttpMethod.GET, "http://localhost/products/1", Map.of(),
            null, StandardCharsets.UTF_8, null);
        Response response = Response.builder().status(status).reason(reason).request(request).build();
        return FeignException.errorStatus("ProductClient#findById", response);
    }
}
