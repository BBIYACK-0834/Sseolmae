package com.sseolmae.payment;

import static org.springframework.http.HttpStatus.*;

import com.sseolmae.reservation.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {
  private final JdbcTemplate db;
  private final TransactionTemplate tx;
  private final ReservationService bookings;
  private final TossGateway gateway;

  PaymentService(
      JdbcTemplate db, TransactionTemplate tx, ReservationService bookings, TossGateway gateway) {
    this.db = db;
    this.tx = tx;
    this.bookings = bookings;
    this.gateway = gateway;
  }

  private Reservation locked(UUID user, UUID id) {
    if (db.queryForList(
            "SELECT id FROM booking WHERE user_id=? AND id=? FOR UPDATE", UUID.class, user, id)
        .isEmpty()) throw new ResponseStatusException(NOT_FOUND, "예약을 찾을 수 없습니다.");
    return bookings.get(user, id);
  }

  private String savedKey(UUID id) {
    return db.queryForObject(
        "SELECT payment_key FROM payment WHERE booking_id=?", String.class, id);
  }

  public Reservation retry(UUID user, UUID id) {
    var r = bookings.get(user, id);
    if (r.status().equals("CONFIRMED")) return r;
    if (!r.status().equals("PROCESSING"))
      throw new ResponseStatusException(CONFLICT, "승인 확인 중인 예약만 재조회할 수 있습니다.");
    return confirm(user, id, savedKey(id), r.amount());
  }

  public Reservation confirm(UUID user, UUID id, String key, int amount) {
    gateway.requireSetup();
    var booking =
        tx.execute(
            transaction -> {
              var r = locked(user, id);
              if (r.amount() != amount)
                throw new ResponseStatusException(BAD_REQUEST, "주문 금액이 일치하지 않습니다.");
              if (r.status().equals("PROCESSING") || r.status().equals("CONFIRMED")) {
                if (!savedKey(id).equals(key))
                  throw new ResponseStatusException(CONFLICT, "결제 정보가 일치하지 않습니다.");
                return r;
              }
              if (!r.status().equals("PENDING"))
                throw new ResponseStatusException(CONFLICT, "결제 가능한 예약이 아닙니다.");
              db.update("INSERT INTO payment(booking_id,payment_key) VALUES(?,?)", id, key);
              db.update("UPDATE booking SET status='PROCESSING' WHERE id=?", id);
              return r;
            });
    if (booking.status().equals("CONFIRMED")) return booking;
    TossGateway.Result result;
    try {
      result = gateway.confirm(id, key, booking.amount());
    } catch (RestClientException e) {
      throw new ResponseStatusException(BAD_GATEWAY, "결제 결과를 확인 중입니다. 내 예약에서 승인 확인을 다시 눌러 주세요.");
    }
    if (result == null
        || !id.toString().equals(result.orderId())
        || !key.equals(result.paymentKey())
        || result.totalAmount() != amount)
      throw new ResponseStatusException(BAD_GATEWAY, "결제사 응답을 확인할 수 없습니다.");
    if (result.status().equals("ABORTED")
        || result.status().equals("EXPIRED")
        || result.status().equals("CANCELED")) {
      db.update("UPDATE booking SET status='CANCELLED' WHERE id=? AND status='PROCESSING'", id);
      throw new ResponseStatusException(CONFLICT, "결제가 종료되었습니다. 다시 예약해 주세요.");
    }
    if (!"DONE".equals(result.status()) || result.approvedAt() == null)
      throw new ResponseStatusException(CONFLICT, "승인이 완료되지 않았습니다. 내 예약에서 다시 확인해 주세요.");
    return tx.execute(
        transaction -> {
          locked(user, id);
          db.update("UPDATE payment SET approved_at=? WHERE booking_id=?", result.approvedAt(), id);
          db.update("UPDATE booking SET status='CONFIRMED' WHERE id=?", id);
          return bookings.get(user, id);
        });
  }
}
