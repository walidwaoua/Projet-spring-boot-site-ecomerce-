package com.ecommerce.commande.services;

import com.ecommerce.commande.clients.*;
import com.ecommerce.commande.dtos.*;
import com.ecommerce.commande.entities.Order;
import com.ecommerce.commande.enums.OrderStatus;
import com.ecommerce.commande.messaging.OrderEventPublisher;
import com.ecommerce.commande.repositories.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {
 @Test void calculatesPriceFromCatalogueAndReservesBeforeConfirmation() {
  var orders=mock(OrderRepository.class); var lines=mock(OrderLineRepository.class); var users=mock(UserClient.class); var products=mock(ProductClient.class); var stock=mock(StockClient.class);
  class StubPublisher extends OrderEventPublisher {
   boolean confirmedPublished;
   StubPublisher() { super(null); }
   @Override public void publishOrderConfirmed(Order order) { confirmedPublished = true; }
   @Override public void publishOrderCancelled(Order order) {}
  }
  var publisher = new StubPublisher();
  when(users.findById(4L)).thenReturn(new UserClient.UserInfo(4L,"A","a@b.c","CLIENT",true));
  when(products.findById(9L)).thenReturn(new ProductClient.ProductInfo(9L,"Book",new BigDecimal("12.50"),null,null,"DISPONIBLE"));
  when(orders.save(any(Order.class))).thenAnswer(inv -> { Order o=inv.getArgument(0); o.setId(2L); return o; });
  when(lines.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
  OrderService service=new OrderService(orders,lines,users,products,stock,publisher);
  OrderResponse result=service.create(new CreateOrderRequest(4L,List.of(new CreateOrderLineRequest(9L,2))));
  assertEquals(new BigDecimal("25.00"),result.montantTotal()); assertEquals(OrderStatus.CONFIRMEE,result.statut());
  verify(stock).reserve(List.of(new StockClient.StockQuantity(9L,2)));
  assertTrue(publisher.confirmedPublished);
 }

 @Test void rejectsInactiveUserBeforeWritingOrder() {
  var orders=mock(OrderRepository.class); var users=mock(UserClient.class);
  when(users.findById(4L)).thenReturn(new UserClient.UserInfo(4L,"A","a@b.c","CLIENT",false));
  OrderService service=new OrderService(orders,mock(OrderLineRepository.class),users,mock(ProductClient.class),mock(StockClient.class),new OrderEventPublisher(null) {
   @Override public void publishOrderConfirmed(Order order) {}
   @Override public void publishOrderCancelled(Order order) {}
  });
  assertThrows(com.ecommerce.commande.exceptions.InvalidReferenceException.class, () -> service.create(new CreateOrderRequest(4L,List.of(new CreateOrderLineRequest(9L,1)))));
  verify(orders,never()).save(any());
 }
}
