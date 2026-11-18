package com.ecommerce.catalogue.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long id) {
        super("Produit introuvable avec l'id " + id);
    }
}
