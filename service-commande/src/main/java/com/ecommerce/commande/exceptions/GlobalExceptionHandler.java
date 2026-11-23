package com.ecommerce.commande.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(OrderNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<Map<String, String>> conflict(InvalidOrderStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(InvalidReferenceException.class)
    public ResponseEntity<Map<String, String>> invalidReference(InvalidReferenceException exception) { return ResponseEntity.unprocessableEntity().body(Map.of("error", exception.getMessage())); }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<Map<String, String>> externalUnavailable(ExternalServiceException exception) { return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", exception.getMessage())); }

    @ExceptionHandler(feign.FeignException.class)
    public ResponseEntity<Map<String, String>> remoteFailure(feign.FeignException exception) {
        HttpStatus status = exception.status() == 409 || exception.status() == 404 ? HttpStatus.CONFLICT : HttpStatus.SERVICE_UNAVAILABLE;
        String message = status == HttpStatus.CONFLICT ? "Réservation de stock impossible" : "Service externe indisponible";
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalidRequest(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse("Données invalides");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "Corps de requête invalide"));
    }
}
