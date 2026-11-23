package com.ecommerce.commande.clients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@FeignClient(name="service-stock", url="${services.stock.url:}")
public interface StockClient {
    @PostMapping("/stock/reservations") Map<Long,Integer> reserve(@RequestBody List<StockQuantity> request);
    @PostMapping("/stock/reservations/release") void release(@RequestBody List<StockQuantity> request);
    @GetMapping("/stock/product/{productId}") StockInfo findByProduct(@PathVariable("productId") Long id);
    record StockQuantity(Long productId, Integer quantite) {}
    record StockInfo(Long id, Long productId, Integer quantite) {}
}
