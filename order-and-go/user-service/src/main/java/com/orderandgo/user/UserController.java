package com.orderandgo.user;

import com.orderandgo.common.ApiException;
import static com.orderandgo.common.Contracts.*;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(10);
    // 존재하지 않는 계정도 비밀번호 검사를 수행해 응답시간 차이를 줄인다.
    private final String dummyHash=encoder.encode("unused-dummy-password");
    public UserController(JdbcTemplate db,TransactionTemplate tx) {this.db=db;this.tx=tx;}

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest r) {
        if (r.password().getBytes(StandardCharsets.UTF_8).length>72 || r.name().isBlank())
            throw new ApiException(400,"INVALID_INPUT","비밀번호는 UTF-8 기준 72바이트 이하로 입력해 주세요.");
        String id=UUID.randomUUID().toString(), email=r.email().strip().toLowerCase(Locale.ROOT);
        String hash=encoder.encode(r.password());
        try {
            return tx.execute(s -> {
                db.update("INSERT INTO app_users(id,email,name,password_hash) VALUES(?,?,?,?)",id,email,r.name().strip(),hash);
                return session(new UserView(id,email,r.name().strip()));
            });
        } catch (DuplicateKeyException e) {throw new ApiException(409,"EMAIL_EXISTS","이미 가입된 이메일입니다.");}
    }
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r) {
        var rows=db.queryForList("SELECT * FROM app_users WHERE email=?",r.email().strip().toLowerCase(Locale.ROOT));
        boolean valid=encoder.matches(r.password().getBytes(StandardCharsets.UTF_8).length>72 ? "" : r.password(),
            rows.isEmpty()?dummyHash:(String)rows.get(0).get("PASSWORD_HASH"));
        if (rows.isEmpty() || !valid) throw new ApiException(401,"INVALID_CREDENTIALS","이메일 또는 비밀번호를 확인해 주세요.");
        var row=rows.get(0);
        return session(new UserView((String)row.get("ID"),(String)row.get("EMAIL"),(String)row.get("NAME")));
    }
    @GetMapping("/me")
    public UserView me(@RequestHeader(value="Authorization",defaultValue="") String auth) {
        var users=db.query("SELECT u.id,u.email,u.name FROM app_users u JOIN sessions s ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>?",
            (rs,n)->new UserView(rs.getString(1),rs.getString(2),rs.getString(3)),hash(token(auth)),Instant.now().getEpochSecond());
        if(users.isEmpty()) throw new ApiException(401,"LOGIN_REQUIRED","로그인이 필요합니다.");
        return users.get(0);
    }
    @DeleteMapping("/session")
    public Map<String,Boolean> logout(@RequestHeader(value="Authorization",defaultValue="") String auth) {
        db.update("DELETE FROM sessions WHERE token_hash=?",hash(token(auth)));
        return Map.of("success",true);
    }
    private AuthResponse session(UserView user) {
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("DELETE FROM sessions WHERE expires_at<?",Instant.now().getEpochSecond());
        db.update("INSERT INTO sessions VALUES(?,?,?)",hash(token),user.id(),Instant.now().plusSeconds(86400).getEpochSecond());
        return new AuthResponse(token,user);
    }
    private String token(String auth) {
        if(!auth.startsWith("Bearer ") || auth.length()<20) throw new ApiException(401,"LOGIN_REQUIRED","로그인이 필요합니다.");
        return auth.substring(7);
    }
    private String hash(String token) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
}
