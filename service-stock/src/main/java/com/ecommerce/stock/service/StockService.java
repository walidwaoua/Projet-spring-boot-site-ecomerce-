package com.ecommerce.stock.service;

import com.ecommerce.stock.client.ProductClient;
import com.ecommerce.stock.dto.*;
import com.ecommerce.stock.entity.Stock;
import com.ecommerce.stock.repository.StockRepository;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class StockService {
    private final StockRepository repository;
    private final ProductClient products;
    public StockService(StockRepository repository, ProductClient products) { this.repository = repository; this.products = products; }

    @Transactional(readOnly = true) public List<StockResponse> findAll() { return repository.findAll().stream().map(this::response).toList(); }
    @Transactional(readOnly = true) public StockResponse findById(Long id) { return response(get(id)); }
    @Transactional(readOnly = true) public StockResponse findByProduct(Long productId) {
        return response(repository.findByProductId(productId).orElseThrow(() -> new StockNotFoundException("Stock introuvable pour le produit " + productId)));
    }
    @Transactional public StockResponse create(StockRequest request) {
        validateProduct(request.productId());
        if (repository.findByProductId(request.productId()).isPresent()) throw new StockConflictException("Un stock existe déjà pour ce produit");
        return response(repository.save(new Stock(request.productId(), request.quantite())));
    }
    @Transactional public StockResponse update(Long id, StockRequest request) {
        Stock stock = get(id); validateProduct(request.productId());
        repository.findByProductId(request.productId()).filter(other -> !other.getId().equals(id)).ifPresent(other -> { throw new StockConflictException("Un stock existe déjà pour ce produit"); });
        if (!Objects.equals(stock.getProductId(), request.productId())) throw new StockConflictException("Le produit d'un stock existant ne peut pas être remplacé");
        stock.setQuantite(request.quantite()); return response(repository.save(stock));
    }
    @Transactional public void delete(Long id) { repository.delete(get(id)); }

    @Transactional public Map<Long, Integer> reserve(List<StockQuantity> quantities) {
        Map<Long, Integer> requested = aggregate(quantities);
        Map<Long, Stock> locked = new LinkedHashMap<>();
        requested.keySet().stream().sorted().forEach(productId -> locked.put(productId, repository.findByProductIdForUpdate(productId)
            .orElseThrow(() -> new StockNotFoundException("Stock introuvable pour le produit " + productId))));
        requested.forEach((productId, quantity) -> {
            Stock stock = locked.get(productId);
            if (stock.getQuantite() < quantity) throw new InsufficientStockException("Stock insuffisant pour le produit " + productId);
        });
        Map<Long, Integer> remaining = new LinkedHashMap<>();
        requested.forEach((productId, quantity) -> { Stock stock = locked.get(productId); stock.setQuantite(stock.getQuantite() - quantity); remaining.put(productId, stock.getQuantite()); });
        repository.saveAll(locked.values()); return remaining;
    }
    @Transactional public void release(List<StockQuantity> quantities) {
        Map<Long, Integer> requested = aggregate(quantities);
        requested.keySet().stream().sorted().forEach(productId -> {
            Stock stock = repository.findByProductIdForUpdate(productId).orElseThrow(() -> new StockNotFoundException("Stock introuvable pour le produit " + productId));
            stock.setQuantite(Math.addExact(stock.getQuantite(), requested.get(productId))); repository.save(stock);
        });
    }
    private Map<Long,Integer> aggregate(List<StockQuantity> quantities) {
        if (quantities == null || quantities.isEmpty()) throw new IllegalArgumentException("La réservation doit contenir au moins un produit");
        Map<Long,Integer> result = new TreeMap<>();
        for (StockQuantity q : quantities) { if (q.productId() == null || q.quantite() == null || q.quantite() <= 0) throw new IllegalArgumentException("Produit et quantité doivent être valides"); result.merge(q.productId(), q.quantite(), Math::addExact); }
        return result;
    }
    private Stock get(Long id) { return repository.findById(id).orElseThrow(() -> new StockNotFoundException("Stock introuvable : " + id)); }
    private void validateProduct(Long id) { try { var p = products.findById(id); if (!"DISPONIBLE".equals(p.statut())) throw new InvalidProductException("Le produit n'est pas disponible"); } catch (FeignException.NotFound e) { throw new InvalidProductException("Produit introuvable : " + id); } }
    private StockResponse response(Stock s) { return new StockResponse(s.getId(), s.getProductId(), s.getQuantite()); }
}
