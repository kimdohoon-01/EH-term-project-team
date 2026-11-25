package com.orderandgo.order;

import com.orderandgo.common.ApiException;
import static com.orderandgo.common.Contracts.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api", "/shop/api"})
public class ApiController {
    private final RemoteServices remote;
    private final OrderService orders;
    private final OrderStore store;
    @Value("${app.allow-test-payment:false}") boolean allowTest;
    public ApiController(RemoteServices remote,OrderService orders,OrderStore store) {this.remote=remote;this.orders=orders;this.store=store;}
    @GetMapping("/config") public Map<String,Boolean> config(){return Map.of("testPayments",allowTest);}
    @GetMapping("/menus") public List<MenuItem> menus(){return remote.menus();}
    @PostMapping("/auth/register") public AuthResponse register(@Valid @RequestBody RegisterRequest r){return remote.register(r);}
    @PostMapping("/auth/login") public AuthResponse login(@Valid @RequestBody LoginRequest r){return remote.login(r);}
    @GetMapping("/auth/me") public UserView me(@RequestHeader(value="Authorization",defaultValue="") String auth){return remote.me(auth);}
    @DeleteMapping("/auth/session") public Map<String,Boolean> logout(@RequestHeader(value="Authorization",defaultValue="") String auth){remote.logout(auth);return Map.of("success",true);}
    @PostMapping("/orders") public OrderView create(@RequestHeader(value="Authorization",defaultValue="") String auth,
        @RequestHeader(value="Idempotency-Key",defaultValue="") String key,@Valid @RequestBody OrderRequest r) {
        return orders.create(remote.me(auth).id(),key,r);
    }
    @GetMapping("/orders") public List<OrderView> list(@RequestHeader(value="Authorization",defaultValue="") String auth){return store.list(remote.me(auth).id());}
    @GetMapping("/orders/{id}") public OrderView get(@RequestHeader(value="Authorization",defaultValue="") String auth,@PathVariable String id) {
        String userId=remote.me(auth).id();var order=store.find(id);
        if(!order.userId().equals(userId)) throw new ApiException(404,"NOT_FOUND","주문을 찾을 수 없습니다.");
        return order.view();
    }
    @GetMapping("/notifications") public List<NotificationView> notifications(@RequestHeader(value="Authorization",defaultValue="") String auth){return remote.notifications(remote.me(auth).id());}
    @PatchMapping("/notifications/{id}/read") public Map<String,Boolean> read(@RequestHeader(value="Authorization",defaultValue="") String auth,@PathVariable String id){remote.read(id,remote.me(auth).id());return Map.of("success",true);}
}
