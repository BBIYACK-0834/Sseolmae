package com.sseolmae.payment;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PaymentRecovery {
  private record Pending(UUID id, UUID user, String key, int amount) {}
  private final JdbcTemplate db;
  private final TossGateway gateway;
  private final PaymentService payments;

  PaymentRecovery(JdbcTemplate db, TossGateway gateway, PaymentService payments) {
    this.db = db;
    this.gateway = gateway;
    this.payments = payments;
  }

  @Scheduled(initialDelay = 60000, fixedDelay = 60000)
  public void reconcile() {
    var rows = db.query("""
        SELECT b.id,b.user_id,p.payment_key,b.amount FROM payment p JOIN booking b ON b.id=p.booking_id
        WHERE b.status='PROCESSING' AND p.next_check_at<=CURRENT_TIMESTAMP
        ORDER BY p.next_check_at LIMIT 20
        """, (r,i) -> new Pending(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getInt(4)));
    for (var row : rows) {
      if (db.update("UPDATE payment SET next_check_at=? WHERE booking_id=? AND next_check_at<=CURRENT_TIMESTAMP",
          OffsetDateTime.now().plusMinutes(5), row.id()) == 0) continue;
      try {
        payments.apply(row.user(), row.id(), row.key(), row.amount(), gateway.lookup(row.id()));
      } catch (RestClientException | ResponseStatusException unresolved) {
        LoggerFactory.getLogger(PaymentRecovery.class).warn("Payment reconciliation pending for order {}", row.id());
      }
    }
  }
}
