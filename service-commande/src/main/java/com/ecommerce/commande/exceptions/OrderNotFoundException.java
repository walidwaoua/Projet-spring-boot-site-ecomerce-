package com.ecommerce.commande.exceptions;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long orderId) {
        super("Commande introuvable : " + orderId);
    }
}
