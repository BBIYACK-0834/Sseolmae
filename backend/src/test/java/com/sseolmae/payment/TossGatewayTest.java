package com.sseolmae.payment;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClientException;

class TossGatewayTest {
  private final UUID order = UUID.randomUUID();
  private final AtomicReference<String> sentKey = new AtomicReference<>();
  private HttpServer server;
  private TossGateway gateway;

  @BeforeEach void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.start();
    gateway = new TossGateway("test","http://127.0.0.1:"+server.getAddress().getPort()+"/v1/payments");
  }
  @AfterEach void stop() {server.stop(0);}

  void stub(String path,int status,String body) {
    server.createContext("/v1/payments/"+path, exchange -> {
      if (path.equals("confirm")) sentKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
      var bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type","application/json");
      exchange.sendResponseHeaders(status,bytes.length);
      try (var out = exchange.getResponseBody()) {out.write(bytes);}
    });
  }

  @Test void lostApprovalResponseLooksUpOrder() {
    stub("confirm",503,"{}");
    stub("orders/"+order,200,"{\"orderId\":\""+order+"\",\"paymentKey\":\"test\",\"totalAmount\":100,\"status\":\"DONE\",\"approvedAt\":\"2026-01-01T00:00:00Z\"}");
    assertEquals("DONE",gateway.confirm(order,"test",100).status());
    assertEquals(order.toString(),sentKey.get());
  }
  @Test void rateLimitedAndMissingOrderIsUncertain() {
    stub("confirm",429,"{}");stub("orders/"+order,404,"{}");
    assertThrows(RestClientException.class,()->gateway.confirm(order,"key",100));
  }
  @Test void confirmedCardRejectionEndsAttempt() {
    stub("confirm",400,"{\"code\":\"REJECT_CARD_PAYMENT\"}");stub("orders/"+order,404,"{}");
    assertEquals("ABORTED",gateway.confirm(order,"key",100).status());
  }
}
