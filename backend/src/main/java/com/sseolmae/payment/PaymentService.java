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

  public Reservation refund(UUID user, UUID id) {
    var booking = tx.execute(transaction -> {
      var r = locked(user,id);
      if (r.refundRequested()) return r;
      if (!"CONFIRMED".equals(r.status()) || r.admittedAt()!=null || !r.startsAt().isAfter(java.time.OffsetDateTime.now()))
        throw new ResponseStatusException(CONFLICT,"시작 전 미입장 확정 예약만 환불할 수 있습니다.");
      gateway.requireSetup();
      db.update("UPDATE booking SET refund_requested=TRUE WHERE id=?",id);
      return r;
    });
    if ("CANCELLED".equals(booking.status())) return booking;
    String key = savedKey(id);
    TossGateway.Result result;
    try {
      result = gateway.refund(id,key,booking.amount());
    } catch (RestClientException uncertain) {
      try { result = gateway.lookup(id); }
      catch (RestClientException unavailable) {
        throw new ResponseStatusException(BAD_GATEWAY,"환불 확인 중입니다. 내 예약에서 다시 확인해 주세요.");
      }
    }
    if (result == null || !"CANCELED".equals(result.status()) || !key.equals(result.paymentKey())
        || !id.toString().equals(result.orderId()) || result.totalAmount()!=booking.amount()
        || !"KRW".equals(result.currency()) || !Integer.valueOf(0).equals(result.balanceAmount()))
      throw new ResponseStatusException(CONFLICT,"환불 확인 중입니다. 내 예약에서 다시 확인해 주세요.");
    return tx.execute(transaction -> {
      locked(user,id);
      db.update("UPDATE booking SET status='CANCELLED' WHERE id=? AND refund_requested=TRUE AND status='CONFIRMED'",id);
      return bookings.get(user,id);
    });
  }

  public Reservation retry(UUID user, UUID id) {
    var r = bookings.get(user, id);
    if (r.status().equals("CONFIRMED")) return r;
    if (!r.status().equals("PROCESSING"))
      throw new ResponseStatusException(CONFLICT, "승인 확인 중인 예약만 재조회할 수 있습니다.");
    return confirm(user, id, savedKey(id), r.amount());
  }

  public Reservation confirm(UUID user, UUID id, String key, int amount) {

    var booking =
        tx.execute(
            transaction -> {
              // Serialize hold expiry and promotion with seat allocation for this slot.
              UUID slot = bookings.get(user, id).slotId();
              db.queryForObject("SELECT id FROM visit_slot WHERE id=? FOR UPDATE", UUID.class, slot);
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
              gateway.requireSetup();
              try {
                db.update("INSERT INTO payment(booking_id,payment_key) VALUES(?,?)", id, key);
              } catch (org.springframework.dao.DuplicateKeyException duplicate) {
                throw new ResponseStatusException(CONFLICT, "이미 사용된 결제 정보입니다.");
              }
              db.update("UPDATE booking SET status='PROCESSING' WHERE id=?", id);
              return r;
            });
    if (booking.status().equals("CONFIRMED")) return booking;
    gateway.requireSetup();
    TossGateway.Result result;
    try {
      result = booking.status().equals("PROCESSING") &&
          !booking.expiresAt().isAfter(java.time.OffsetDateTime.now())
          ? gateway.lookup(id) : gateway.confirm(id, key, booking.amount());
    } catch (RestClientException e) {
      throw new ResponseStatusException(BAD_GATEWAY, "결제 결과를 확인 중입니다. 내 예약에서 승인 확인을 다시 눌러 주세요.");
    }
    var resolved = apply(user, id, key, amount, result);
    if ("CANCELLED".equals(resolved.status()))
      throw new ResponseStatusException(CONFLICT, "결제가 종료되었습니다. 다시 예약해 주세요.");
    return resolved;
  }

  Reservation apply(UUID user, UUID id, String key, int amount, TossGateway.Result result) {
    if (result == null || !id.toString().equals(result.orderId()) || !key.equals(result.paymentKey())
        || result.totalAmount() != amount || result.status() == null)
      throw new ResponseStatusException(BAD_GATEWAY, "결제사 응답을 확인 중입니다. 추가 결제 없이 내 예약에서 확인해 주세요.");
    return tx.execute(transaction -> {
      var current = locked(user, id);
      if (!"PROCESSING".equals(current.status())) {
        if ("CONFIRMED".equals(current.status())) return current;
        throw new ResponseStatusException(CONFLICT, "종료된 예약입니다. 내 예약을 확인해 주세요.");
      }
      switch (result.status()) {
        case "ABORTED", "EXPIRED", "CANCELED" ->
          db.update("UPDATE booking SET status='CANCELLED' WHERE id=?", id);
        case "DONE" -> {
          if (result.approvedAt() == null || !"KRW".equals(result.currency())
              || !Integer.valueOf(amount).equals(result.balanceAmount()))
            throw new ResponseStatusException(BAD_GATEWAY, "승인 시각, 통화 및 결제 잔액을 확인 중입니다.");
          db.update("UPDATE payment SET approved_at=? WHERE booking_id=?", result.approvedAt(), id);
          db.update("UPDATE booking SET status='CONFIRMED' WHERE id=?", id);
        }
        default -> throw new ResponseStatusException(CONFLICT, "승인 확인 중입니다. 추가 결제 없이 내 예약에서 확인해 주세요.");
      }
      return bookings.get(user, id);
    });
  }
}
