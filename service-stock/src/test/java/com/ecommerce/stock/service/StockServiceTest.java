package com.ecommerce.stock.service;

import com.ecommerce.stock.client.ProductClient;
import com.ecommerce.stock.dto.StockQuantity;
import com.ecommerce.stock.entity.Stock;
import com.ecommerce.stock.repository.StockRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StockServiceTest {
    @Test void reservesAllRequestedProducts() {
        StockRepository repository = mock(StockRepository.class);
        Stock first = new Stock(7L, 8); Stock second = new Stock(8L, 5);
        when(repository.findByProductIdForUpdate(7L)).thenReturn(Optional.of(first));
        when(repository.findByProductIdForUpdate(8L)).thenReturn(Optional.of(second));
        StockService service = new StockService(repository, mock(ProductClient.class));
        var remaining = service.reserve(List.of(new StockQuantity(7L, 3), new StockQuantity(8L, 2)));
        assertEquals(5, remaining.get(7L)); assertEquals(3, remaining.get(8L));
        verify(repository).saveAll(anyCollection());
    }

    @Test void insufficientStockDoesNotMutateAnyProduct() {
        StockRepository repository = mock(StockRepository.class);
        Stock first = new Stock(7L, 8); Stock second = new Stock(8L, 1);
        when(repository.findByProductIdForUpdate(7L)).thenReturn(Optional.of(first));
        when(repository.findByProductIdForUpdate(8L)).thenReturn(Optional.of(second));
        StockService service = new StockService(repository, mock(ProductClient.class));
        assertThrows(InsufficientStockException.class, () -> service.reserve(List.of(new StockQuantity(7L, 3), new StockQuantity(8L, 2))));
        assertEquals(8, first.getQuantite()); assertEquals(1, second.getQuantite());
        verify(repository, never()).saveAll(anyCollection());
    }
}
