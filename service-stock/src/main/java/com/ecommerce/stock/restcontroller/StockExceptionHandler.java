package com.ecommerce.stock.restcontroller;
import com.ecommerce.stock.service.*;
import feign.FeignException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestControllerAdvice
public class StockExceptionHandler {
    @ExceptionHandler(StockNotFoundException.class) ResponseEntity<?> notFound(RuntimeException e) { return response(HttpStatus.NOT_FOUND,e); }
    @ExceptionHandler({StockConflictException.class, InsufficientStockException.class}) ResponseEntity<?> conflict(RuntimeException e) { return response(HttpStatus.CONFLICT,e); }
    @ExceptionHandler(InvalidProductException.class) ResponseEntity<?> invalidProduct(RuntimeException e) { return response(HttpStatus.UNPROCESSABLE_ENTITY,e); }
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> badRequest(RuntimeException e) { return response(HttpStatus.BAD_REQUEST,e); }
    @ExceptionHandler(FeignException.class) ResponseEntity<?> unavailable(FeignException e) { return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error","Service catalogue indisponible")); }
    private ResponseEntity<?> response(HttpStatus status, RuntimeException e) { return ResponseEntity.status(status).body(Map.of("error",e.getMessage())); }
}
