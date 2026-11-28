package com.sseolmae.auth;

import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class Accounts {
  private final JdbcTemplate db;

  public Accounts(JdbcTemplate db) {
    this.db = db;
  }

  public UUID id(OAuth2AuthenticationToken auth) {
    String provider = auth.getAuthorizedClientRegistrationId();
    String subject = auth.getName();
    String select = "SELECT id FROM venue_user WHERE provider=? AND subject=?";
    var existing = db.queryForList(select, UUID.class, provider, subject);
    if (!existing.isEmpty()) return existing.getFirst();
    UUID id = UUID.randomUUID();
    try {
      db.update("INSERT INTO venue_user(id,provider,subject) VALUES(?,?,?)", id, provider, subject);
      return id;
    } catch (DuplicateKeyException concurrentLogin) {
      return db.queryForObject(select, UUID.class, provider, subject);
    }
  }
}
