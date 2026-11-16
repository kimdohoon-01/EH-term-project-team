package com.orderandgo.notification;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages="com.orderandgo")
@EnableScheduling
public class NotificationApplication {
    public static void main(String[] args) { SpringApplication.run(NotificationApplication.class,args); }
}
