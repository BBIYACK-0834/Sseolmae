package com.sseolmae.reservation;

import static org.springframework.http.HttpStatus.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationService {
  private final JdbcTemplate db;
  private final Clock clock;

  public ReservationService(JdbcTemplate db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  public record Slot(
      UUID id, TicketType ticketType, OffsetDateTime startsAt, int price, int childPrice, int remaining) {}

  private static final String OCCUPIED =
      """
      COALESCE((SELECT SUM(b.adults+b.children) FROM booking b WHERE b.slot_id=s.id
        AND (b.status IN ('CONFIRMED','PROCESSING') OR (b.status='PENDING' AND b.expires_at>?))),0)
      """;
  private static final String SLOT =
      "SELECT s.*, s.capacity-" + OCCUPIED + " AS remaining FROM visit_slot s ";
  private static final String BOOKING =
      """
      SELECT b.*,s.ticket_type,s.starts_at FROM booking b JOIN visit_slot s ON s.id=b.slot_id
      """;

  private Slot slot(ResultSet r, int index) throws SQLException {
    return new Slot(
        r.getObject("id", UUID.class),
        TicketType.valueOf(r.getString("ticket_type")),
        r.getObject("starts_at", OffsetDateTime.class),
        r.getInt("price"),
        r.getObject("child_price") == null ? r.getInt("price") : r.getInt("child_price"),
        r.getInt("remaining"));
  }

  private Reservation booking(ResultSet r, int index) throws SQLException {
    var expiry = r.getObject("expires_at", OffsetDateTime.class);
    String state = r.getString("status");
    if (state.equals("PENDING") && !expiry.isAfter(now())) state = "EXPIRED";
    return new Reservation(
        r.getObject("id", UUID.class),
        r.getObject("slot_id", UUID.class),
        TicketType.valueOf(r.getString("ticket_type")),
        r.getObject("starts_at", OffsetDateTime.class),
        r.getInt("adults"),
        r.getInt("children"),
        r.getInt("amount"),
        state,
        expiry, r.getBoolean("refund_requested"), r.getObject("admitted_at", OffsetDateTime.class));
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock);
  }

  public List<Slot> slots() {
    return db.query(
        SLOT + "WHERE s.sale_open AND s.starts_at>? ORDER BY s.starts_at,s.ticket_type", this::slot, now(), now());
  }

  public List<Reservation> list(UUID user) {
    return db.query(BOOKING + " WHERE b.user_id=? ORDER BY b.expires_at DESC", this::booking, user);
  }

  public Reservation get(UUID user, UUID id) {
    return db.query(BOOKING + " WHERE b.id=? AND b.user_id=?", this::booking, id, user).stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "예약을 찾을 수 없습니다."));
  }

  @Transactional
  public Reservation create(UUID user, ReservationRequest request) {
    int guests = request.adults() + request.children();
    if (guests < 1 || guests > 20)
      throw new ResponseStatusException(BAD_REQUEST, "총 인원은 1~20명입니다.");
    db.queryForObject("SELECT id FROM venue_user WHERE id=? FOR UPDATE", UUID.class, user);
    var previous =
        db.query(
            BOOKING + " WHERE b.user_id=? AND b.request_id=?",
            this::booking,
            user,
            request.requestId());
    if (!previous.isEmpty()) {
      Reservation saved = previous.getFirst();
      if (!saved.slotId().equals(request.slotId())
          || saved.adults() != request.adults()
          || saved.children() != request.children())
        throw new ResponseStatusException(CONFLICT, "같은 요청 번호의 예약 내용이 다릅니다.");
      return saved;
    }
    if (db.queryForList(
            "SELECT id FROM visit_slot WHERE id=? FOR UPDATE", UUID.class, request.slotId())
        .isEmpty()) throw new ResponseStatusException(NOT_FOUND, "회차를 찾을 수 없습니다.");
    var time = now();
    var slot = db.queryForObject(SLOT + "WHERE s.id=?", this::slot, time, request.slotId());
    if (!Boolean.TRUE.equals(db.queryForObject("SELECT sale_open FROM visit_slot WHERE id=?",Boolean.class,slot.id())) || !slot.startsAt().isAfter(time) || slot.remaining() < guests)
      throw new ResponseStatusException(CONFLICT, "예약 가능한 잔여 좌석을 확인해 주세요.");
    var expires = time.plusMinutes(10);
    if (slot.startsAt().isBefore(expires)) expires = slot.startsAt();
    UUID id = UUID.randomUUID();
    db.update(
        """
        INSERT INTO booking(id,user_id,slot_id,request_id,adults,children,amount,status,expires_at)
        VALUES(?,?,?,?,?,?,?,'PENDING',?)
        """,
        id,
        user,
        slot.id(),
        request.requestId(),
        request.adults(),
        request.children(),
        Math.toIntExact((long)slot.price()*request.adults() + (long)slot.childPrice()*request.children()),
        expires);
    return get(user, id);
  }

  @Transactional
  public void cancel(UUID user, UUID id) {
    var booking = get(user, id);
    if (booking.status().equals("CANCELLED") || booking.status().equals("EXPIRED")) return;
    if (db.update(
            "UPDATE booking SET status='CANCELLED' WHERE id=? AND user_id=? AND status='PENDING'",
            id,
            user)
        == 0) throw new ResponseStatusException(CONFLICT, "결제 대기 예약만 취소할 수 있습니다.");
  }
}
