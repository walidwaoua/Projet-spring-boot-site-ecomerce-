package com.ecommerce.commande.dtos;
import com.ecommerce.commande.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
public record EnrichedOrderResponse(Long id, UserSummary utilisateur, LocalDateTime date, OrderStatus statut, BigDecimal montantTotal, List<EnrichedLine> lignes) {
 public record UserSummary(Long id, String nom, String email, String role, boolean actif) {}
 public record EnrichedLine(Long id, Long productId, String nomProduit, Integer quantiteCommandee, Integer stockDisponible, BigDecimal prixUnitaire) {}
}
