package com.orderandgo.notification;

import static com.orderandgo.common.Contracts.*;
import static org.junit.jupiter.api.Assertions.*;
import com.orderandgo.common.ApiException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;

class NotificationControllerTest {
    @Test void eventsAreDeduplicatedAndOnlyOwnerCanMarkRead(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        var notifications=new NotificationController(new JdbcTemplate(ds));
        var event=new NotificationRequest("order:CONFIRMED","owner","order","CONFIRMED");
        notifications.create(event);notifications.create(event);assertEquals(1,notifications.list("owner").size());
        var id=notifications.list("owner").get(0).id();
        assertThrows(ApiException.class,()->notifications.read(id,"someone-else"));
        notifications.read(id,"owner");assertTrue(notifications.list("owner").get(0).read());
    }
}
