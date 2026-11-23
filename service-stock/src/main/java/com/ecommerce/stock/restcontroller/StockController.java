package com.ecommerce.stock.restcontroller;
import com.ecommerce.stock.dto.*;
import com.ecommerce.stock.service.StockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/stock")
public class StockController {
    private final StockService service;
    public StockController(StockService service) { this.service = service; }
    @GetMapping public List<StockResponse> findAll() { return service.findAll(); }
    @GetMapping("/{id}") public StockResponse findById(@PathVariable Long id) { return service.findById(id); }
    @GetMapping("/product/{productId}") public StockResponse findByProduct(@PathVariable Long productId) { return service.findByProduct(productId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public StockResponse create(@Valid @RequestBody StockRequest request) { return service.create(request); }
    @PutMapping("/{id}") public StockResponse update(@PathVariable Long id, @Valid @RequestBody StockRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { service.delete(id); }
    @PostMapping("/reservations") public Map<Long,Integer> reserve(@Valid @RequestBody List<@Valid StockQuantity> request) { return service.reserve(request); }
    @PostMapping("/reservations/release") @ResponseStatus(HttpStatus.NO_CONTENT) public void release(@Valid @RequestBody List<@Valid StockQuantity> request) { service.release(request); }
}
