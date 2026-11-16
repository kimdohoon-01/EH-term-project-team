package com.orderandgo.order;

import static com.orderandgo.common.Contracts.*;
import com.orderandgo.common.ApiException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class OrderStore {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    public OrderStore(JdbcTemplate db,TransactionTemplate tx) {this.db=db;this.tx=tx;}
    public record StoredOrder(String id,String userId,String method,String fingerprint,OrderView view) {}
    public StoredOrder find(String id) {
        var rows=db.queryForList("SELECT * FROM cafe_orders WHERE id=?",id);
        if(rows.isEmpty()) throw new ApiException(404,"NOT_FOUND","주문을 찾을 수 없습니다.");
        var r=rows.get(0);
        var lines=db.query("SELECT * FROM order_lines WHERE order_id=? ORDER BY menu_id",
            (rs,n)->new OrderLine(rs.getString("menu_id"),rs.getString("name"),rs.getInt("price"),rs.getInt("quantity")),id);
        String status=(String)r.get("STATUS"),message=(String)r.get("MESSAGE");
        if(!Set.of("CONFIRMED","CANCELLED").contains(status) && !currentBackend(id)) {
            status="LEGACY_REVIEW";message="이전 테스트 서비스의 주문입니다. 기존 처리 결과를 확인해 주세요.";
        }
        return new StoredOrder(id,(String)r.get("USER_ID"),(String)r.get("PAYMENT_METHOD"),(String)r.get("REQUEST_FINGERPRINT"),
            new OrderView(id,status,lines,((Number)r.get("TOTAL")).intValue(),message,(String)r.get("CREATED_AT"),(Boolean)r.get("NOTIFICATION_SENT")));
    }
    public Optional<StoredOrder> byKey(String userId,String key) {
        var ids=db.queryForList("SELECT id FROM cafe_orders WHERE user_id=? AND request_key=?",String.class,userId,key);
        return ids.isEmpty()?Optional.empty():Optional.of(find(ids.get(0)));
    }
    public String create(String userId,String key,String fingerprint,OrderRequest request) {
        String id=UUID.randomUUID().toString();
        tx.executeWithoutResult(s->{
            db.update("INSERT INTO cafe_orders(id,user_id,request_key,request_fingerprint,payment_method,status,message,created_at) VALUES(?,?,?,?,?,'CREATED',?,?)",
                id,userId,key,fingerprint,request.paymentMethod(),"주문을 접수하고 있어요.",Instant.now().toString());
            for(var item:request.items()) db.update("INSERT INTO order_lines(order_id,menu_id,quantity) VALUES(?,?,?)",id,item.menuId(),item.quantity());
            db.update("INSERT INTO order_backends(order_id,backend) VALUES(?,'gyua-v1')",id);
        }); return id;
    }
    public void reserved(String id,Reservation r) {
        tx.executeWithoutResult(s->{
            db.update("DELETE FROM order_lines WHERE order_id=?",id);
            for(var item:r.items()) db.update("INSERT INTO order_lines(order_id,menu_id,name,price,quantity) VALUES(?,?,?,?,?)",id,item.menuId(),item.name(),item.price(),item.quantity());
            db.update("UPDATE cafe_orders SET total=?,status='STOCK_RESERVED',message=? WHERE id=?",r.total(),"재고를 확보했어요.",id);
        });
    }
    public void state(String id,String status,String message) {db.update("UPDATE cafe_orders SET status=?,message=? WHERE id=?",status,message,id);}
    public void notified(String id) {db.update("UPDATE cafe_orders SET notification_sent=TRUE WHERE id=?",id);}
    public List<OrderView> list(String userId) {
        return db.queryForList("SELECT id FROM cafe_orders WHERE user_id=? ORDER BY created_at DESC LIMIT 100",String.class,userId).stream().map(id->find(id).view()).toList();
    }
    private boolean currentBackend(String id) {return db.queryForObject("SELECT COUNT(*) FROM order_backends WHERE order_id=? AND backend='gyua-v1'",Integer.class,id)>0;}
    public List<String> pending() {return db.queryForList("SELECT id FROM cafe_orders o WHERE (status NOT IN ('CONFIRMED','CANCELLED') AND EXISTS(SELECT 1 FROM order_backends b WHERE b.order_id=o.id AND b.backend='gyua-v1')) OR (status IN ('CONFIRMED','CANCELLED') AND notification_sent=FALSE) ORDER BY created_at LIMIT 30",String.class);}
}
