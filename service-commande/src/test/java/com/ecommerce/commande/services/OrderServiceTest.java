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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
  OrderService service=new OrderService(orders,lines,users,products,stock,publisher,new com.ecommerce.commande.clients.FeignResilienceExecutor(2,java.time.Duration.ZERO,5,3,50,java.time.Duration.ofSeconds(10)));
  OrderResponse result=service.create(new CreateOrderRequest(4L,List.of(new CreateOrderLineRequest(9L,2))),"order-test-1").order();
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
  },new com.ecommerce.commande.clients.FeignResilienceExecutor(2,java.time.Duration.ZERO,5,3,50,java.time.Duration.ofSeconds(10)));
  assertThrows(com.ecommerce.commande.exceptions.InvalidReferenceException.class, () -> service.create(new CreateOrderRequest(4L,List.of(new CreateOrderLineRequest(9L,1))),"order-test-2"));
  verify(orders,never()).save(any());
 }

 @Test void replayWithSameIdempotencyKeyReturnsTheExistingOrderWithoutReservingAgain() {
  var orders=mock(OrderRepository.class); var lines=mock(OrderLineRepository.class); var users=mock(UserClient.class); var products=mock(ProductClient.class); var stock=mock(StockClient.class);
  class StubPublisher extends OrderEventPublisher {
   StubPublisher() { super(null); }
   @Override public void publishOrderConfirmed(Order order) {}
   @Override public void publishOrderCancelled(Order order) {}
  }
  AtomicReference<Order> stored = new AtomicReference<>();
  when(orders.findByIdempotencyKey("same-key")).thenAnswer(inv -> Optional.ofNullable(stored.get()));
  when(orders.save(any(Order.class))).thenAnswer(inv -> { Order order=inv.getArgument(0); if(order.getId()==null) order.setId(10L); stored.set(order); return order; });
  when(lines.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
  when(lines.findAllByOrderId(10L)).thenAnswer(inv -> {
   Order order=stored.get(); return order == null ? List.of() : List.of(new com.ecommerce.commande.entities.OrderLine(10L,9L,2,new BigDecimal("12.50")));
  });
  when(users.findById(4L)).thenReturn(new UserClient.UserInfo(4L,"A","a@b.c","CLIENT",true));
  when(products.findById(9L)).thenReturn(new ProductClient.ProductInfo(9L,"Book",new BigDecimal("12.50"),null,null,"DISPONIBLE"));
  OrderService service=new OrderService(orders,lines,users,products,stock,new StubPublisher(),new com.ecommerce.commande.clients.FeignResilienceExecutor(2,java.time.Duration.ZERO,5,3,50,java.time.Duration.ofSeconds(10)));
  var request = new CreateOrderRequest(4L,List.of(new CreateOrderLineRequest(9L,2)));
  var first=service.create(request,"same-key");
  var replay=service.create(request,"same-key");
  assertFalse(first.replayed()); assertTrue(replay.replayed());
  assertEquals(first.order().id(), replay.order().id());
  verify(stock,times(1)).reserve(anyList());
  verify(orders,times(2)).save(any(Order.class)); // initial pending insert and final confirmation, no writes on replay
 }

 @Test void sameIdempotencyKeyWithDifferentPayloadIsRejected() {
  var orders=mock(OrderRepository.class); var existing=new Order(4L,OrderStatus.CONFIRMEE,new BigDecimal("25.00"),"same-key","different-hash"); existing.setId(10L);
  when(orders.findByIdempotencyKey("same-key")).thenReturn(Optional.of(existing));
  OrderService service=new OrderService(orders,mock(OrderLineRepository.class),mock(UserClient.class),mock(ProductClient.class),mock(StockClient.class),new OrderEventPublisher(null) {
   @Override public void publishOrderConfirmed(Order order) {}
   @Override public void publishOrderCancelled(Order order) {}
  },new com.ecommerce.commande.clients.FeignResilienceExecutor(2,java.time.Duration.ZERO,5,3,50,java.time.Duration.ofSeconds(10)));
  assertThrows(com.ecommerce.commande.exceptions.IdempotencyConflictException.class,
   () -> service.create(new CreateOrderRequest(4L,List.of(new CreateOrderLineRequest(99L,1))),"same-key"));
  verify(orders,never()).save(any());
 }
}
