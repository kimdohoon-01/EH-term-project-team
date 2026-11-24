package com.orderandgo.order;

import com.orderandgo.common.ApiException;
import static com.orderandgo.common.Contracts.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 단일 인스턴스용 순차 오케스트레이션. 각 외부 변경은 orderId로 멱등 처리한다. */
@Service
public class OrderService {
    private static final Logger log=LoggerFactory.getLogger(OrderService.class);
    private final OrderStore store;
    private final RemoteServices remote;
    private final boolean allowTest;
    public OrderService(OrderStore store,RemoteServices remote,@Value("${app.allow-test-payment:false}") boolean allowTest) {
        this.store=store;this.remote=remote;this.allowTest=allowTest;
    }
    public synchronized OrderView create(String userId,String key,OrderRequest r) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,100}")) throw new ApiException(400,"IDEMPOTENCY_REQUIRED","주문 요청 식별자가 필요합니다.");
        if(!allowTest && !r.paymentMethod().equals("CARD")) throw new ApiException(400,"TEST_DISABLED","테스트 결제는 로컬 실행에서만 사용할 수 있습니다.");
        if(r.items().stream().map(CartItem::menuId).distinct().count()!=r.items().size()) throw new ApiException(400,"DUPLICATE_MENU","같은 메뉴는 수량으로 합쳐 주세요.");
        String fingerprint=r.paymentMethod()+":"+r.items().stream().sorted(Comparator.comparing(CartItem::menuId))
            .map(i->i.menuId()+"="+i.quantity()).collect(Collectors.joining(","));
        var previous=store.byKey(userId,key);
        if(previous.isPresent()) {
            if(!previous.get().fingerprint().equals(fingerprint)) throw new ApiException(409,"KEY_REUSED","같은 요청 식별자로 다른 주문을 보낼 수 없습니다.");
            return store.find(previous.get().id()).view();
        }
        remote.validateItems(r.items());
        String id=store.create(userId,key,fingerprint,r);
        process(id);
        return store.find(id).view();
    }
    public synchronized void process(String id) {
        var order=store.find(id);
        String status=order.view().status();
        if(status.equals("LEGACY_REVIEW")) return;
        if(Set.of("CREATED","STOCK_PENDING").contains(status)) {
            try {
                var r=remote.reserve(new ReserveRequest(id,order.view().items().stream().map(i->new CartItem(i.menuId(),i.quantity())).toList()));
                if(!r.status().equals("RESERVED")) {store.state(id,"CANCELLED","재고 확보가 취소된 주문입니다.");deliver(id);return;}
                store.reserved(id,r);
            } catch(RestClientResponseException e) {
                if(Set.of(400,404,409).contains(e.getStatusCode().value())) {
                    store.state(id,"CANCELLED","메뉴가 없거나 재고가 부족하여 주문이 취소되었어요.");deliver(id);return;
                }
                store.state(id,"STOCK_PENDING","재고 처리 결과를 확인하고 있어요.");return;
            } catch(RestClientException e) {store.state(id,"STOCK_PENDING","재고 처리 결과를 확인하고 있어요.");return;}
            order=store.find(id);status=order.view().status();
        }
        if(Set.of("STOCK_RESERVED","PAYMENT_PENDING").contains(status)) {
            // 요청 전에 기록한다. 결제 응답 유실 시 재고를 바로 복구하면 안 된다.
            store.state(id,"PAYMENT_PENDING","결제 결과를 확인하고 있어요.");
            try {
                var payment=remote.pay(new PaymentRequest(id,order.view().total(),order.method()));
                if(payment.status().equals("APPROVED")) store.state(id,"CONFIRMED","결제가 완료되어 주문이 확정되었어요.");
                else if(payment.status().equals("DECLINED")) store.state(id,"CANCEL_PENDING","결제가 실패하여 재고를 복구하고 있어요.");
                else return;
            } catch(RestClientException e) {return;}
            status=store.find(id).view().status();
        }
        if(status.equals("CANCEL_PENDING")) {
            try {remote.release(id);store.state(id,"CANCELLED","결제가 실패했어요. 재고 복구 후 주문을 취소했어요.");}
            catch(RestClientException e) {return;}
        }
        deliver(id);
    }
    private void deliver(String id) {
        var o=store.find(id);
        if(o.view().notificationSent() || !Set.of("CONFIRMED","CANCELLED").contains(o.view().status())) return;
        try {remote.notify(new NotificationRequest(id+":"+o.view().status(),o.userId(),id,o.view().status()));store.notified(id);}
        catch(RestClientException e) {log.info("알림 재전송 대기: {}",id);}
    }
    @Scheduled(fixedDelayString="${app.retry-delay-ms:5000}",initialDelay=5000)
    public void retryPending() {
        for(String id:store.pending()) {
            try {process(id);} catch(Exception e) {log.warn("주문 {} 재처리 보류: {}",id,e.getClass().getSimpleName());}
        }
    }
}
