package com.orderandgo.order;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages="com.orderandgo")
@EnableScheduling
public class OrderApplication {
    public static void main(String[] args) { SpringApplication.run(OrderApplication.class,args); }
}
