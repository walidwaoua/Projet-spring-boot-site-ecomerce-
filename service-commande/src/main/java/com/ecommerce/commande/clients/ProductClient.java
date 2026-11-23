package com.ecommerce.commande.clients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.math.BigDecimal;
@FeignClient(name="service-catalogue", url="${services.catalogue.url:}")
public interface ProductClient {
    @GetMapping("/products/{id}") ProductInfo findById(@PathVariable("id") Long id);
    record ProductInfo(Long id, String nom, BigDecimal prix, String description, String categorie, String statut) {}
}
