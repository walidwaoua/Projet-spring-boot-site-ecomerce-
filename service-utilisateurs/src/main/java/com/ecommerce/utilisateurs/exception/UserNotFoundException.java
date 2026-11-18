package com.ecommerce.utilisateurs.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("Utilisateur introuvable avec l'id " + id);
    }
}
