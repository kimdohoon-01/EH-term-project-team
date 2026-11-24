package com.orderandgo.notification;

import com.orderandgo.common.ApiException;
import static com.orderandgo.common.Contracts.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/notifications")
public class NotificationController {
    private final JdbcTemplate db;
    public NotificationController(JdbcTemplate db){this.db=db;}
    @PostMapping
    public Map<String,Boolean> create(@Valid @RequestBody NotificationRequest r) {
        if(!Set.of("CONFIRMED","CANCELLED").contains(r.status())) throw new ApiException(400,"INVALID_STATUS","최종 주문 상태만 알림을 생성합니다.");
        boolean confirmed=r.status().equals("CONFIRMED");
        try {db.update("INSERT INTO notifications(id,event_id,user_id,order_id,title,message,created_at) VALUES(?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),r.eventId(),r.userId(),r.orderId(),confirmed?"주문이 확정되었어요":"주문이 취소되었어요",
            confirmed?"카페에서 주문을 확인했어요. 주문 내역을 확인해 주세요.":"주문이 취소되었어요. 사유는 주문 내역에서 확인할 수 있어요.",Instant.now().toString());}
        catch(DuplicateKeyException alreadyDelivered) { /* 재전송은 중복 알림을 만들지 않는다. */ }
        return Map.of("accepted",true);
    }
    @GetMapping
    public List<NotificationView> list(@RequestParam String userId) {
        return db.query("SELECT * FROM notifications WHERE user_id=? ORDER BY created_at DESC LIMIT 100",
            (rs,n)->new NotificationView(rs.getString("id"),rs.getString("order_id"),rs.getString("title"),rs.getString("message"),rs.getBoolean("is_read"),rs.getString("created_at")),userId);
    }
    @PatchMapping("/{id}/read")
    public Map<String,Boolean> read(@PathVariable String id,@RequestParam String userId) {
        if(db.update("UPDATE notifications SET is_read=TRUE WHERE id=? AND user_id=?",id,userId)==0)
            throw new ApiException(404,"NOT_FOUND","알림을 찾을 수 없습니다.");
        return Map.of("success",true);
    }
}
