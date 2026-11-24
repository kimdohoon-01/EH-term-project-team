package com.orderandgo.user;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages="com.orderandgo")
@EnableScheduling
public class UserApplication {
    public static void main(String[] args) { SpringApplication.run(UserApplication.class,args); }
}
