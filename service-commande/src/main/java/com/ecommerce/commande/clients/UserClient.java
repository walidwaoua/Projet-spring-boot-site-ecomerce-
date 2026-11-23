package com.ecommerce.commande.clients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
@FeignClient(name="service-utilisateurs", url="${services.utilisateurs.url:}")
public interface UserClient {
    @GetMapping("/users/{id}") UserInfo findById(@PathVariable("id") Long id);
    record UserInfo(Long id, String nom, String email, String role, boolean actif) {}
}
