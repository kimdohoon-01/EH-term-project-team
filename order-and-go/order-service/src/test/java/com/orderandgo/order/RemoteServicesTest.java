package com.orderandgo.order;

import static org.junit.jupiter.api.Assertions.*;
import static com.orderandgo.common.Contracts.*;
import com.orderandgo.common.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClientException;

class RemoteServicesTest {
    HttpServer server; RemoteServices remote;
    Map<String,String> responses=new HashMap<>();
    Map<String,String> requests=new HashMap<>();
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            String key=exchange.getRequestMethod()+" "+exchange.getRequestURI().getPath();
            requests.put(key,new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] body=responses.getOrDefault(key,"{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        String url="http://127.0.0.1:"+server.getAddress().getPort();
        remote=new RemoteServices(url,url,url,url,"test-key");
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void reservationUsesReservedPriceAndNumericWireId(){
        responses.put("GET /api/menus/1","{\"id\":1,\"name\":\"Coffee\",\"price\":3500,\"stock\":10,\"active\":true}");
        responses.put("PUT /api/stock/reservations/order-1","{\"orderId\":\"order-1\",\"status\":\"RESERVED\",\"items\":[{\"menuId\":1,\"quantity\":2,\"unitPrice\":4000}],\"totalAmount\":8000}");
        var result=remote.reserve(new ReserveRequest("order-1",List.of(new CartItem("1",2))));
        assertEquals(8000,result.total());assertEquals("Coffee",result.items().get(0).name());
        assertTrue(requests.get("PUT /api/stock/reservations/order-1").contains("\"menuId\":1"));
    }
    @Test void inconsistentReservationCannotReachPayment(){
        responses.put("GET /api/menus/1","{\"id\":1,\"name\":\"Coffee\",\"price\":3500,\"stock\":10,\"active\":true}");
        responses.put("PUT /api/stock/reservations/order-1","{\"orderId\":\"order-1\",\"status\":\"RESERVED\",\"items\":[{\"menuId\":1,\"quantity\":2,\"unitPrice\":3500}],\"totalAmount\":1}");
        assertThrows(RestClientException.class,()->remote.reserve(new ReserveRequest("order-1",List.of(new CartItem("1",2)))));
    }
    @Test void paymentMapsFailureAndRejectsMismatchedOrder(){
        responses.put("POST /api/payments","{\"orderId\":\"order-1\",\"amount\":7000,\"status\":\"FAILED\"}");
        assertEquals("DECLINED",remote.pay(new PaymentRequest("order-1",7000,"TEST_DECLINE")).status());
        assertTrue(requests.get("POST /api/payments").contains("\"simulate\":\"FAILURE\""));
        responses.put("POST /api/payments","{\"orderId\":\"other\",\"amount\":7000,\"status\":\"SUCCEEDED\"}");
        assertThrows(RestClientException.class,()->remote.pay(new PaymentRequest("order-1",7000,"CARD")));
    }
    @Test void restorationMustBeConfirmed(){
        responses.put("POST /api/stock/reservations/order-1/release","{\"orderId\":\"order-1\",\"status\":\"RESERVED\"}");
        assertThrows(RestClientException.class,()->remote.release("order-1"));
    }
    @Test void invalidAndOverflowingIdsAreRejectedBeforeHttp(){
        for(String id:List.of("americano","01","0","9223372036854775808"))
            assertThrows(ApiException.class,()->remote.validateItems(List.of(new CartItem(id,1))));
        assertTrue(requests.isEmpty());
    }
}
