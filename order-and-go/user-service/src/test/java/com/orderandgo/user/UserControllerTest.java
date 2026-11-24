package com.orderandgo.user;

import static com.orderandgo.common.Contracts.*;
import static org.junit.jupiter.api.Assertions.*;
import com.orderandgo.common.ApiException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;

class UserControllerTest {
    JdbcTemplate db;UserController users;
    @BeforeEach void setup(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);db=new JdbcTemplate(ds);
        users=new UserController(db,new TransactionTemplate(new DataSourceTransactionManager(ds)));
    }
    @Test void passwordAndTokensAreStoredAsHashesAndLogoutRevokesSession(){
        var auth=users.register(new RegisterRequest("DoHoon@example.com","Password123!","도훈","서울시 중구 세종대로 110","101호"));
        assertEquals("dohoon@example.com",auth.user().email());assertEquals(auth.user(),users.me("Bearer "+auth.accessToken()));
        assertEquals("서울시 중구 세종대로 110",db.queryForObject("SELECT address FROM user_addresses",String.class));
        assertEquals("101호",db.queryForObject("SELECT address_detail FROM user_addresses",String.class));
        assertNotEquals("Password123!",db.queryForObject("SELECT password_hash FROM app_users",String.class));
        assertNotEquals(auth.accessToken(),db.queryForObject("SELECT token_hash FROM sessions",String.class));
        users.logout("Bearer "+auth.accessToken());assertThrows(ApiException.class,()->users.me("Bearer "+auth.accessToken()));
    }
    @Test void rejectsWeakPasswordsAndInvalidAddressesWithoutCreatingUser(){
        for(String password:List.of("short1!","password123","PASSWORD!!!","12345678!","Pass word1!","비밀번호123!"))
            assertThrows(ApiException.class,()->users.register(new RegisterRequest("a@example.com",password,"도훈","서울시 중구 세종대로 110",null)));
        for(String address:Arrays.asList(null," ","서울","가".repeat(201)))
            assertThrows(ApiException.class,()->users.register(new RegisterRequest("a@example.com","Password123!","도훈",address,null)));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM app_users",Integer.class));
    }
    @Test void oldMemberCanLoginWithoutAddressOrNewPasswordRule(){
        String hash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password123");
        db.update("INSERT INTO app_users VALUES(?,?,?,?)","old","old@example.com","기존회원",hash);
        assertNotNull(users.login(new LoginRequest("old@example.com","password123")).accessToken());
    }
    @Test void rejectsDuplicateEmailAndWrongPassword(){
        users.register(new RegisterRequest("hello@example.com","Password123!","도훈","서울시 중구 세종대로 110","101호"));
        assertThrows(ApiException.class,()->users.register(new RegisterRequest("HELLO@example.com","Password123!","규아","서울시 중구 세종대로 110",null)));
        assertThrows(ApiException.class,()->users.login(new LoginRequest("hello@example.com","wrong")));
        assertNotNull(users.login(new LoginRequest("HELLO@example.com","Password123!")).accessToken());
    }
}
