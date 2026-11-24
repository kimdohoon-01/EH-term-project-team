package com.orderandgo.order;

import com.orderandgo.common.HttpClients;
import com.orderandgo.common.ApiException;
import static com.orderandgo.common.Contracts.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RemoteServices {
    private final RestClient users,menus,payments,notifications;
    public RemoteServices(@Value("${services.user.url}") String userUrl,@Value("${services.menu.url}") String menuUrl,
        @Value("${services.payment.url}") String paymentUrl,@Value("${services.notification.url}") String notificationUrl,
        @Value("${app.internal-key}") String key) {
        users=HttpClients.create(userUrl,key);menus=HttpClients.create(menuUrl,null);
        payments=HttpClients.create(paymentUrl,null);notifications=HttpClients.create(notificationUrl,key);
    }
    public UserView me(String auth) {return users.get().uri("/api/users/me").header("Authorization",auth).retrieve().body(UserView.class);}
    public AuthResponse register(RegisterRequest r) {return users.post().uri("/api/users/register").body(r).retrieve().body(AuthResponse.class);}
    public AuthResponse login(LoginRequest r) {return users.post().uri("/api/users/login").body(r).retrieve().body(AuthResponse.class);}
    public void logout(String auth) {users.delete().uri("/api/users/session").header("Authorization",auth).retrieve().toBodilessEntity();}
    public List<MenuItem> menus() {
        var result=menus.get().uri("/api/menus").retrieve().body(new ParameterizedTypeReference<List<PeerMenu>>(){});
        if(result==null) throw new RestClientException("Menu response was empty");
        return result.stream().filter(PeerMenu::active)
            .map(m->new MenuItem(Long.toString(m.id()),m.name(),"MENU","",m.price(),m.stock())).toList();
    }
    public void validateItems(List<CartItem> items) {for(var item:items) numericId(item.menuId());}
    private long numericId(String value) {
        try {long id=Long.parseLong(value);if(id>0 && Long.toString(id).equals(value))return id;}
        catch(NumberFormatException ignored) {}
        throw new ApiException(400,"INVALID_MENU_ID","메뉴 ID는 양의 정수여야 합니다.");
    }
    public Reservation reserve(ReserveRequest r) {
        // 규아의 재고 응답에는 이름이 없으므로 차감 요청 전에 메뉴명을 조회한다.
        Map<Long,PeerMenu> names=new HashMap<>();
        List<PeerCartItem> requested=new ArrayList<>();
        for(var item:r.items()) {
            long id=numericId(item.menuId());
            var menu=menus.get().uri("/api/menus/{id}",id).retrieve().body(PeerMenu.class);
            if(menu==null || menu.id()!=id) throw new RestClientException("Invalid menu response");
            names.put(id,menu);requested.add(new PeerCartItem(id,item.quantity()));
        }
        var response=menus.put().uri("/api/stock/reservations/{id}",r.orderId())
            .body(new PeerReserveRequest(requested)).retrieve().body(PeerReservation.class);
        if(response==null || !r.orderId().equals(response.orderId())) throw new RestClientException("Invalid reservation response");
        if("RELEASED".equals(response.status()))return new Reservation(r.orderId(),"RELEASED",List.of(),0);
        if(!"RESERVED".equals(response.status()) || response.items()==null) throw new RestClientException("Unknown reservation status");
        Map<Long,Integer> expected=new HashMap<>();requested.forEach(i->expected.put(i.menuId(),i.quantity()));
        List<OrderLine> lines=new ArrayList<>();long total=0;
        for(var item:response.items()) {
            Integer quantity=expected.remove(item.menuId());
            if(quantity==null || quantity!=item.quantity() || item.unitPrice()<1 || item.unitPrice()>1_000_000)
                throw new RestClientException("Reservation items do not match the order");
            total+=(long)item.unitPrice()*item.quantity();
            lines.add(new OrderLine(Long.toString(item.menuId()),names.get(item.menuId()).name(),item.unitPrice(),item.quantity()));
        }
        if(!expected.isEmpty() || total!=response.totalAmount() || total<1 || total>Integer.MAX_VALUE)
            throw new RestClientException("Invalid reservation total");
        return new Reservation(response.orderId(),"RESERVED",lines,(int)total);
    }
    public void release(String id) {
        var response=menus.post().uri("/api/stock/reservations/{id}/release",id).retrieve().body(PeerReservation.class);
        if(response==null || !id.equals(response.orderId()) || !"RELEASED".equals(response.status()))
            throw new RestClientException("Stock restoration was not confirmed");
    }
    public PaymentResult pay(PaymentRequest r) {
        var response=payments.post().uri("/api/payments")
            .body(new PeerPaymentRequest(r.orderId(),r.amount(),r.method().equals("TEST_DECLINE")?"FAILURE":"SUCCESS"))
            .retrieve().body(PeerPayment.class);
        if(response==null || !r.orderId().equals(response.orderId()) || r.amount()!=response.amount())
            throw new RestClientException("Payment result does not match the order");
        String status=switch(response.status()==null?"":response.status()) {
            case "SUCCEEDED" -> "APPROVED";
            case "FAILED" -> "DECLINED";
            default -> "PENDING";
        };
        return new PaymentResult(r.orderId(),status,null);
    }
    public record PeerMenu(long id,String name,int price,long stock,boolean active) {}
    public record PeerCartItem(long menuId,int quantity) {}
    public record PeerReserveRequest(List<PeerCartItem> items) {}
    public record PeerReservedItem(long menuId,int quantity,int unitPrice) {}
    public record PeerReservation(String orderId,String status,List<PeerReservedItem> items,long totalAmount) {}
    public record PeerPaymentRequest(String orderId,long amount,String simulate) {}
    public record PeerPayment(String orderId,long amount,String status,String failureReason,String processedAt) {}
    public void notify(NotificationRequest r) {notifications.post().uri("/internal/notifications").body(r).retrieve().toBodilessEntity();}
    public List<NotificationView> notifications(String userId) {return notifications.get().uri(b->b.path("/internal/notifications").queryParam("userId",userId).build()).retrieve().body(new ParameterizedTypeReference<List<NotificationView>>(){});}
    public void read(String id,String userId) {notifications.patch().uri(b->b.path("/internal/notifications/{id}/read").queryParam("userId",userId).build(id)).retrieve().toBodilessEntity();}
}
