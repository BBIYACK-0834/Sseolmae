package com.sseolmae.auth;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class SessionController {
  private final Accounts accounts;

  public SessionController(Accounts accounts) {
    this.accounts = accounts;
  }

  public record Session(UUID userId, boolean kakaoReady, String clientKey, String csrf) {}

  @GetMapping("/api/session")
  Session session(
      OAuth2AuthenticationToken auth,
      CsrfToken csrf,
      @Value("${venue.kakao-id}") String id,
      @Value("${venue.kakao-secret}") String secret,
      @Value("${venue.toss-client}") String clientKey,
      @Value("${venue.toss-secret}") String paymentSecret) {
    return new Session(
        auth == null ? null : accounts.id(auth),
        !id.isBlank() && !secret.isBlank(),
        paymentSecret.isBlank() ? "" : clientKey,
        csrf.getToken());
  }
}
