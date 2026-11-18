package com.ecommerce.catalogue.service;

import com.ecommerce.catalogue.exception.ProductNotFoundException;
import com.ecommerce.catalogue.model.Product;
import com.ecommerce.catalogue.model.ProductRequest;
import com.ecommerce.catalogue.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ProductService {
    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<Product> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    public Product create(ProductRequest request) {
        Product product = new Product();
        apply(product, request);
        return repository.save(product);
    }

    public Product update(Long id, ProductRequest request) {
        Product product = findById(id);
        apply(product, request);
        return repository.save(product);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private void apply(Product product, ProductRequest request) {
        product.setNom(request.nom());
        product.setPrix(request.prix());
        product.setDescription(request.description());
        product.setCategorie(request.categorie());
        product.setStatut(request.statut());
    }
}
