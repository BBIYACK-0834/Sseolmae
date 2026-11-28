package com.sseolmae.payment;

import static org.springframework.http.HttpStatus.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class TossGateway {
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Failure(String code) {}

  public record Result(
      String paymentKey,
      String orderId,
      int totalAmount,
      String status,
      OffsetDateTime approvedAt, String currency, Integer balanceAmount) {}

  private final String secret;
  private final RestClient client;

  @org.springframework.beans.factory.annotation.Autowired
  public TossGateway(@Value("${venue.toss-secret}") String secret) {
    this(secret, "https://api.tosspayments.com/v1/payments");
  }

  TossGateway(String secret, String baseUrl) {
    this.secret = secret;
    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofSeconds(20));
    client =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .defaultHeaders(headers -> headers.setBasicAuth(secret, ""))
            .build();
  }

  public void requireSetup() {
    if (secret.isBlank())
      throw new ResponseStatusException(SERVICE_UNAVAILABLE, "결제 연동을 준비하고 있습니다.");
  }

  public Result lookup(UUID id) {
    requireSetup();
    return client.get().uri("/orders/{id}", id).retrieve().body(Result.class);
  }

  public Result confirm(UUID id, String key, int amount) {
    try {
      return client
          .post()
          .uri("/confirm")
          .header("Idempotency-Key", id.toString())
          .body(Map.of("paymentKey", key, "orderId", id, "amount", amount))
          .retrieve()
          .body(Result.class);
    } catch (RestClientResponseException e) {
      // A prior attempt may already have succeeded. Read the provider's authoritative order state.
      try {
        return lookup(id);
      } catch (RestClientResponseException lookup) {
        // Release inventory only for a definitive rejection and a missing provider order.
        if (lookup.getStatusCode().value() == 404 && e.getStatusCode().is4xxClientError()) {
          var failure = e.getResponseBodyAs(Failure.class);
          if (failure != null && failure.code() != null
              && java.util.Set.of(
                      "REJECT_CARD_PAYMENT",
                      "PAYMENT_EXPIRED")
                  .contains(failure.code())) {
            return new Result(key, id.toString(), amount, "ABORTED", null, null, null);
          }
        }
        throw lookup;
      }
    } catch (org.springframework.web.client.ResourceAccessException timeout) {
      return lookup(id);
    }
  }
}
