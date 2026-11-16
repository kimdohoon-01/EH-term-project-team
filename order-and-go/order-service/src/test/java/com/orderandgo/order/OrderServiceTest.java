package com.orderandgo.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.orderandgo.common.Contracts.*;
import com.orderandgo.common.ApiException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

class OrderServiceTest {
    OrderStore store;RemoteServices remote;OrderService service;JdbcTemplate db;
    OrderRequest request=new OrderRequest(List.of(new CartItem("1",2)),"CARD");
    @BeforeEach void setup(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        db=new JdbcTemplate(ds);
        store=new OrderStore(db,new TransactionTemplate(new DataSourceTransactionManager(ds)));
        remote=mock(RemoteServices.class);service=new OrderService(store,remote,true);
        when(remote.reserve(any())).thenAnswer(i->{ReserveRequest r=i.getArgument(0);return new Reservation(r.orderId(),"RESERVED",List.of(new OrderLine("1","아메리카노",3500,2)),7000);});
        when(remote.pay(any())).thenAnswer(i->{PaymentRequest r=i.getArgument(0);return new PaymentResult(r.orderId(),"APPROVED","payment-1");});
    }
    @Test void legacyPendingOrderCannotChargeNewBackend(){
        String id=store.create("u","legacy-001","legacy",request);
        db.update("DELETE FROM order_backends WHERE order_id=?",id);
        service.process(id);
        assertEquals("LEGACY_REVIEW",store.find(id).view().status());
        assertFalse(store.pending().contains(id));
        verifyNoInteractions(remote);
    }
    @Test void confirmedUsesAuthoritativePriceAndNotifies(){
        var order=service.create("user-1","request-001",request);
        assertEquals("CONFIRMED",order.status());assertEquals(7000,order.total());assertTrue(order.notificationSent());
        verify(remote).pay(new PaymentRequest(order.id(),7000,"CARD"));verify(remote,never()).release(any());
    }
    @Test void declineRestoresBeforeCancelling(){
        doReturn(new PaymentResult("order","DECLINED","p")).when(remote).pay(any());
        var order=service.create("user-1","request-002",request);
        assertEquals("CANCELLED",order.status());
        var sequence=inOrder(remote);sequence.verify(remote).reserve(any());sequence.verify(remote).pay(any());sequence.verify(remote).release(order.id());sequence.verify(remote).notify(any());
    }
    @Test void insufficientStockNeverCharges(){
        doThrow(new HttpClientErrorException(HttpStatus.CONFLICT)).when(remote).reserve(any());
        var order=service.create("user-1","request-003",request);
        assertEquals("CANCELLED",order.status());verify(remote,never()).pay(any());verify(remote,never()).release(any());
    }
    @Test void ambiguousPaymentDoesNotRestoreAndRetriesSameOrder(){
        doThrow(new ResourceAccessException("lost response")).doReturn(new PaymentResult("x","APPROVED","p")).when(remote).pay(any());
        var order=service.create("user-1","request-004",request);
        assertEquals("PAYMENT_PENDING",order.status());verify(remote,never()).release(any());
        service.process(order.id());assertEquals("CONFIRMED",store.find(order.id()).view().status());
        verify(remote,times(2)).pay(new PaymentRequest(order.id(),7000,"CARD"));verify(remote,times(1)).reserve(any());
    }
    @Test void failedRestoreStaysPendingUntilRestoreSucceeds(){
        doReturn(new PaymentResult("x","DECLINED","p")).when(remote).pay(any());
        doThrow(new ResourceAccessException("offline")).doNothing().when(remote).release(any());
        var order=service.create("u","request-005",request);assertEquals("CANCEL_PENDING",order.status());assertFalse(order.notificationSent());
        service.process(order.id());assertEquals("CANCELLED",store.find(order.id()).view().status());verify(remote,times(1)).pay(any());
    }
    @Test void duplicateRequestReturnsSameOrderWithoutChargingAgain(){
        var first=service.create("u","request-006",request);var second=service.create("u","request-006",request);
        assertEquals(first.id(),second.id());verify(remote,times(1)).reserve(any());verify(remote,times(1)).pay(any());
        assertThrows(ApiException.class,()->service.create("u","request-006",new OrderRequest(List.of(new CartItem("1",3)),"CARD")));
    }
    @Test void notificationFailureCannotCancelPaidOrder(){
        doThrow(new ResourceAccessException("offline")).doNothing().when(remote).notify(any());
        var order=service.create("u","request-007",request);assertEquals("CONFIRMED",order.status());assertFalse(order.notificationSent());
        service.process(order.id());assertTrue(store.find(order.id()).view().notificationSent());verify(remote,times(1)).pay(any());verify(remote,never()).release(any());
    }
    @Test void ambiguousInventoryRetriesSameReservation(){
        doThrow(new ResourceAccessException("lost response"))
            .doAnswer(i->{ReserveRequest r=i.getArgument(0);return new Reservation(r.orderId(),"RESERVED",List.of(new OrderLine("1","아메리카노",3500,2)),7000);}).when(remote).reserve(any());
        var order=service.create("u","request-008",request);assertEquals("STOCK_PENDING",order.status());verify(remote,never()).pay(any());
        service.process(order.id());assertEquals("CONFIRMED",store.find(order.id()).view().status());
        verify(remote,times(2)).reserve(new ReserveRequest(order.id(),request.items()));
    }
}

