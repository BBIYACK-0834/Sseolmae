package com.sseolmae;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SseolmaeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SseolmaeApplication.class, args);
    }

    @Bean
    Clock venueClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
