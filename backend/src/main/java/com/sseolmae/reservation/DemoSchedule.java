package com.sseolmae.reservation;

import java.time.*;
import java.util.UUID;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
class DemoSchedule implements ApplicationRunner {
  private final JdbcTemplate db;
  private final Clock clock;

  DemoSchedule(JdbcTemplate db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  public void run(ApplicationArguments ignored) {
    for (int day = 1; day <= 7; day++) {
      var start =
          LocalDate.now(clock)
              .plusDays(day)
              .atTime(14, 0)
              .atZone(clock.getZone())
              .toOffsetDateTime();
      for (var type : TicketType.values()) {
        int price =
            switch (type) {
              case MEDIA_ART -> 12000;
              case SNOW_SLED -> 15000;
              case COMBO -> 22000;
            };
        db.update(
            """
            INSERT INTO visit_slot(id,ticket_type,starts_at,capacity,price)
            SELECT ?,?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM visit_slot WHERE ticket_type=? AND starts_at=?)
            """,
            UUID.randomUUID(),
            type.name(),
            start,
            30,
            price,
            type.name(),
            start);
      }
    }
  }
}
