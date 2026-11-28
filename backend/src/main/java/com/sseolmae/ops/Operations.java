package com.sseolmae.ops;

import com.sseolmae.auth.Accounts;
import com.sseolmae.reservation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/staff")
public class Operations {
  private final Accounts accounts;
  private final Staff staff;
  private final JdbcTemplate db;
  private final ReservationService reservations;
  public Operations(Accounts accounts, Staff staff, JdbcTemplate db, ReservationService reservations) {
    this.accounts=accounts; this.staff=staff; this.db=db; this.reservations=reservations;
  }
  private UUID operator(Authentication auth) {
    UUID id=accounts.id(auth);
    if (!staff.includes(id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"운영자 권한이 필요합니다.");
    return id;
  }
  record Terms(@Min(1) @Max(100000) int capacity,@Min(1) @Max(10000000) int price,
      @Min(1) @Max(10000000) int childPrice, @NotNull Boolean saleOpen) {}
  record Create(@NotNull TicketType ticketType,@NotNull @Future OffsetDateTime startsAt,@Valid @NotNull Terms terms) {}
  @GetMapping("/slots") List<Map<String,Object>> slots(Authentication auth) {
    operator(auth);
    return db.queryForList("SELECT id,ticket_type,starts_at,capacity,price,COALESCE(child_price,price) child_price,sale_open FROM visit_slot WHERE starts_at>CURRENT_TIMESTAMP ORDER BY starts_at,ticket_type");
  }
  @PostMapping("/slots") @ResponseStatus(HttpStatus.CREATED)
  void create(Authentication auth,@Valid @RequestBody Create input) {
    operator(auth); var t=input.terms();
    try { db.update("INSERT INTO visit_slot(id,ticket_type,starts_at,capacity,price,child_price,sale_open) VALUES(?,?,?,?,?,?,?)",
        UUID.randomUUID(),input.ticketType().name(),input.startsAt(),t.capacity(),t.price(),t.childPrice(),t.saleOpen()); }
    catch (DuplicateKeyException duplicate) {throw conflict("같은 이용권의 회차가 이미 있습니다.");}
  }
  @PutMapping("/slots/{id}") @Transactional
  void edit(Authentication auth,@PathVariable UUID id,@Valid @RequestBody Terms t) {
    operator(auth);
    if(db.queryForList("SELECT id FROM visit_slot WHERE id=? AND starts_at>CURRENT_TIMESTAMP FOR UPDATE",UUID.class,id).isEmpty())
      throw conflict("수정 가능한 회차가 없습니다.");
    int held=db.queryForObject("SELECT COALESCE(SUM(adults+children),0) FROM booking WHERE slot_id=? AND (status IN ('CONFIRMED','PROCESSING') OR (status='PENDING' AND expires_at>CURRENT_TIMESTAMP))",Integer.class,id);
    if(t.capacity()<held) throw conflict("확보된 좌석보다 정원을 줄일 수 없습니다.");
    db.update("UPDATE visit_slot SET capacity=?,price=?,child_price=?,sale_open=? WHERE id=?",t.capacity(),t.price(),t.childPrice(),t.saleOpen(),id);
  }
  private ResponseStatusException conflict(String text){return new ResponseStatusException(HttpStatus.CONFLICT,text);}
  private Reservation find(UUID id) {
    var users=db.queryForList("SELECT user_id FROM booking WHERE id=?",UUID.class,id);
    if(users.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"예약이 없습니다.");
    return reservations.get(users.getFirst(),id);
  }
  @GetMapping("/reservations/{id}") Reservation find(Authentication auth,@PathVariable UUID id){operator(auth);return find(id);}
  @PostMapping("/reservations/{id}/admit") @Transactional
  Reservation admit(Authentication auth,@PathVariable UUID id){
    UUID operator=operator(auth);
    var r=find(id);
    var seoul=ZoneId.of("Asia/Seoul");
    if(!r.startsAt().atZoneSameInstant(seoul).toLocalDate().equals(LocalDate.now(seoul)))throw conflict("방문 당일만 입장할 수 있습니다.");
    if(db.update("UPDATE booking SET admitted_at=CURRENT_TIMESTAMP,admitted_by=? WHERE id=? AND status='CONFIRMED' AND refund_requested=FALSE AND admitted_at IS NULL",operator,id)!=1)
      throw conflict("이미 입장했거나 입장할 수 없는 예약입니다.");
    return find(id);
  }
}
