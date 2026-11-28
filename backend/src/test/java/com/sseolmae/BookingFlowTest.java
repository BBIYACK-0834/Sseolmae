package com.sseolmae;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sseolmae.payment.TossGateway;
import com.sseolmae.reservation.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:flow;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class BookingFlowTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @MockitoBean TossGateway toss;
  private UUID slot;

  @BeforeEach
  void prepare() {
    slot = UUID.randomUUID();
    db.update(
        "INSERT INTO visit_slot VALUES(?,?,?,?,?)",
        slot,
        "MEDIA_ART",
        OffsetDateTime.now().plusDays(1),
        2,
        12000);
  }

  private RequestPostProcessor user(String id) {
    return oauth2Login().attributes(a -> a.put("sub", id));
  }

  private String payload(UUID requestId) throws Exception {
    return json.writeValueAsString(new ReservationRequest(slot, requestId, 1, 1));
  }

  private UUID reserve(String subject, String input) throws Exception {
    var response =
        mvc.perform(
                post("/api/reservations")
                    .with(user(subject))
                    .with(csrf())
                    .contentType("application/json")
                    .content(input))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(
        json.readTree(response.getResponse().getContentAsString()).get("id").asText());
  }

  @Test
  void accessValidationAndInventory() throws Exception {
    mvc.perform(get("/api/session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.csrf").isString());
    mvc.perform(get("/api/reservations")).andExpect(status().isUnauthorized());
    String input = payload(UUID.randomUUID());
    mvc.perform(
            post("/api/reservations")
                .with(user("u1"))
                .contentType("application/json")
                .content(input))
        .andExpect(status().isForbidden());
    UUID id = reserve("u1", input);
    assertEquals(id, reserve("u1", input));
    mvc.perform(get("/api/reservations/" + id).with(user("u2"))).andExpect(status().isNotFound());
    mvc.perform(
            post("/api/reservations")
                .with(user("u2"))
                .with(csrf())
                .contentType("application/json")
                .content(payload(UUID.randomUUID())))
        .andExpect(status().isConflict());
    mvc.perform(delete("/api/reservations/" + id).with(user("u1")).with(csrf()))
        .andExpect(status().isNoContent());
    reserve("u2", payload(UUID.randomUUID()));
  }

  @Test
  void confirmsOnlyOnceAndChecksAmount() throws Exception {
    UUID id = reserve("buyer", payload(UUID.randomUUID()));
    var key = "test-" + id;
    var body =
        json.createObjectNode()
            .put("orderId", id.toString())
            .put("paymentKey", key)
            .put("amount", 1);
    mvc.perform(
            post("/api/payments/confirm")
                .with(user("buyer"))
                .with(csrf())
                .contentType("application/json")
                .content(body.toString()))
        .andExpect(status().isBadRequest());
    when(toss.confirm(id, key, 24000))
        .thenReturn(
            new TossGateway.Result(key, id.toString(), 24000, "DONE", OffsetDateTime.now(), "KRW", 24000));
    body.put("amount", 24000);
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/payments/confirm")
                  .with(user("buyer"))
                  .with(csrf())
                  .contentType("application/json")
                  .content(body.toString()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("CONFIRMED"));
    verify(toss, times(1)).confirm(id, key, 24000);
  }

  @Test
  void expiredHoldCanBeResold() throws Exception {
    UUID expired = reserve("old", payload(UUID.randomUUID()));
    db.update(
        "UPDATE booking SET expires_at=? WHERE id=?",
        OffsetDateTime.now().minusMinutes(1),
        expired);
    mvc.perform(get("/api/reservations/" + expired).with(user("old")))
        .andExpect(jsonPath("$.status").value("EXPIRED"));
    reserve("new", payload(UUID.randomUUID()));
  }

  @Test
  void concurrentCustomersCannotTakeSameLastSeats() throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Integer> attempt =
          () -> {
            start.await();
            return mvc.perform(
                    post("/api/reservations")
                        .with(user(UUID.randomUUID().toString()))
                        .with(csrf())
                        .contentType("application/json")
                        .content(payload(UUID.randomUUID())))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var first = pool.submit(attempt);
      var second = pool.submit(attempt);
      start.countDown();
      assertEquals(java.util.Set.of(201, 409), java.util.Set.of(first.get(), second.get()));
    }
  }

  @Test
  void uncertainApprovalCanBeRecoveredWithoutReleasingSeats() throws Exception {
    UUID id = reserve("interrupted", payload(UUID.randomUUID()));
    var key = "recovery-" + id;
    var body =
        json.createObjectNode()
            .put("orderId", id.toString())
            .put("paymentKey", key)
            .put("amount", 24000);
    when(toss.confirm(id, key, 24000))
        .thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"))
        .thenReturn(
            new TossGateway.Result(key, id.toString(), 24000, "DONE", OffsetDateTime.now(), "KRW", 24000));
    mvc.perform(
            post("/api/payments/confirm")
                .with(user("interrupted"))
                .with(csrf())
                .contentType("application/json")
                .content(body.toString()))
        .andExpect(status().isBadGateway());
    mvc.perform(get("/api/reservations/" + id).with(user("interrupted")))
        .andExpect(jsonPath("$.status").value("PROCESSING"));
    mvc.perform(post("/api/payments/" + id + "/retry").with(user("interrupted")).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  @Test
  void rejectedPaymentReleasesSeats() throws Exception {
    UUID id = reserve("declined", payload(UUID.randomUUID()));
    var key = "decline-" + id;
    when(toss.confirm(id, key, 24000))
        .thenReturn(new TossGateway.Result(key, id.toString(), 24000, "ABORTED", null,null,null));
    var body =
        json.createObjectNode()
            .put("orderId", id.toString())
            .put("paymentKey", key)
            .put("amount", 24000);
    mvc.perform(
            post("/api/payments/confirm")
                .with(user("declined"))
                .with(csrf())
                .contentType("application/json")
                .content(body.toString()))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/reservations/" + id).with(user("declined")))
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    reserve("next-buyer", payload(UUID.randomUUID()));
  }

  @Autowired com.sseolmae.payment.PaymentRecovery recovery;

  @Test
  void recoveryRejectsMismatchesThenSettlesWithoutBrowser() throws Exception {
    UUID id = reserve("offline", payload(UUID.randomUUID()));
    String key = "offline-" + id;
    String body = json.createObjectNode().put("orderId", id.toString()).put("paymentKey",key)
        .put("amount",24000).toString();
    mvc.perform(post("/api/payments/confirm").with(user("other")).with(csrf())
        .contentType("application/json").content(body)).andExpect(status().isNotFound());
    when(toss.confirm(id,key,24000)).thenReturn(new TossGateway.Result(key,id.toString(),24000,null,null,null,null));
    mvc.perform(post("/api/payments/confirm").with(user("offline")).with(csrf())
        .contentType("application/json").content(body)).andExpect(status().isBadGateway());
    when(toss.lookup(id)).thenReturn(new TossGateway.Result(key,id.toString(),24000,"DONE",OffsetDateTime.now(),"KRW",12000))
        .thenReturn(new TossGateway.Result(key,id.toString(),24000,"DONE",OffsetDateTime.now(),"KRW",24000));
    recovery.reconcile();
    mvc.perform(get("/api/reservations/"+id).with(user("offline"))).andExpect(jsonPath("$.status").value("PROCESSING"));
    db.update("UPDATE payment SET next_check_at=CURRENT_TIMESTAMP WHERE booking_id=?",id);
    recovery.reconcile();
    mvc.perform(get("/api/reservations/"+id).with(user("offline"))).andExpect(jsonPath("$.status").value("CONFIRMED"));
    verify(toss,times(1)).confirm(id,key,24000);
  }
}
