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
        var auth=users.register(new RegisterRequest("DoHoon@example.com","password123","도훈"));
        assertEquals("dohoon@example.com",auth.user().email());assertEquals(auth.user(),users.me("Bearer "+auth.accessToken()));
        assertNotEquals("password123",db.queryForObject("SELECT password_hash FROM app_users",String.class));
        assertNotEquals(auth.accessToken(),db.queryForObject("SELECT token_hash FROM sessions",String.class));
        users.logout("Bearer "+auth.accessToken());assertThrows(ApiException.class,()->users.me("Bearer "+auth.accessToken()));
    }
    @Test void rejectsDuplicateEmailAndWrongPassword(){
        users.register(new RegisterRequest("hello@example.com","password123","도훈"));
        assertThrows(ApiException.class,()->users.register(new RegisterRequest("HELLO@example.com","password123","규아")));
        assertThrows(ApiException.class,()->users.login(new LoginRequest("hello@example.com","wrong")));
        assertNotNull(users.login(new LoginRequest("HELLO@example.com","password123")).accessToken());
    }
}
