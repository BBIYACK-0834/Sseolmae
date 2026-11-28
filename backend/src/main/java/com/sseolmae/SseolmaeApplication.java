package com.sseolmae;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class SseolmaeApplication {

  public static void main(String[] args) {
    var app = new SpringApplication(SseolmaeApplication.class);
    app.addInitializers(context -> ProductionSettings.validate(context.getEnvironment()));
    app.run(args);
  }

  @Bean
  Clock venueClock() {
    return Clock.system(ZoneId.of("Asia/Seoul"));
  }
}
