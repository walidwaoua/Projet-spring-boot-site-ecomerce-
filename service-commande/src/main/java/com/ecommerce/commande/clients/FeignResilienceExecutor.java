package com.ecommerce.commande.clients;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** Applies bounded retry and circuit-breaker protection to remote validation calls. */
@Component
public class FeignResilienceExecutor {
    private final CircuitBreakerConfig circuitBreakerConfig;
    private final RetryConfig retryConfig;
    private final ConcurrentMap<String, Retry> retries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    public FeignResilienceExecutor(
        @Value("${v6.feign.retry.max-attempts:2}") int maxAttempts,
        @Value("${v6.feign.retry.wait-duration:200ms}") Duration retryWait,
        @Value("${v6.feign.circuit-breaker.sliding-window-size:5}") int slidingWindowSize,
        @Value("${v6.feign.circuit-breaker.minimum-number-of-calls:3}") int minimumNumberOfCalls,
        @Value("${v6.feign.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
        @Value("${v6.feign.circuit-breaker.open-wait-duration:10s}") Duration openWait) {
        this.retryConfig = RetryConfig.custom()
            .maxAttempts(maxAttempts)
            .waitDuration(retryWait)
            .retryOnException(FeignResilienceExecutor::isTransient)
            .build();
        this.circuitBreakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(slidingWindowSize)
            .minimumNumberOfCalls(minimumNumberOfCalls)
            .failureRateThreshold(failureRateThreshold)
            .waitDurationInOpenState(openWait)
            .recordException(FeignResilienceExecutor::isTransient)
            .build();
    }

    public <T> T execute(String service, Supplier<T> remoteCall) {
        Retry retry = retries.computeIfAbsent(service, name -> Retry.of(name, retryConfig));
        CircuitBreaker circuitBreaker = circuitBreakers.computeIfAbsent(service,
            name -> CircuitBreaker.of(name, circuitBreakerConfig));
        Supplier<T> retried = Retry.decorateSupplier(retry, remoteCall);
        return CircuitBreaker.decorateSupplier(circuitBreaker, retried).get();
    }

    private static boolean isTransient(Throwable failure) {
        return failure instanceof RetryableException
            || failure instanceof FeignException feign && (feign.status() == 429 || feign.status() >= 500);
    }
}
