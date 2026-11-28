package com.sseolmae;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSettingsTest {
  private MockEnvironment settings() {
    var env = new MockEnvironment().withProperty("venue.origin","https://tickets.example")
        .withProperty("spring.datasource.url","jdbc:h2:file:/data/venue")
        .withProperty("spring.datasource.password","fixture")
        .withProperty("server.servlet.session.cookie.secure","true");
    env.setActiveProfiles("prod");
    return env;
  }

  @Test void permitsClosedSalesAndMatchedLiveConfiguration() {
    var env = settings();
    assertDoesNotThrow(() -> ProductionSettings.validate(env));
    env.setProperty("venue.toss-client","live_ck_fixture");
    env.setProperty("venue.toss-secret","live_sk_fixture");
    assertDoesNotThrow(() -> ProductionSettings.validate(env));
  }

  @Test void rejectsIncorrectProductionSettings() {
    for (var pair : new String[][]{
        {"venue.origin","http://tickets.example"}, {"venue.origin","https://tickets.example/path"},
        {"spring.datasource.url","jdbc:h2:mem:venue"}, {"spring.datasource.password",""},
        {"server.servlet.session.cookie.secure","false"}, {"venue.toss-client","test_ck_fixture"},
        {"venue.toss-secret","live_sk_fixture"}, {"NAVER_CLIENT_ID","fixture"}}) {
      var env = settings().withProperty(pair[0],pair[1]);
      assertThrows(IllegalStateException.class, () -> ProductionSettings.validate(env),pair[0]);
    }
    var env = settings();
    env.setActiveProfiles("prod","demo");
    assertThrows(IllegalStateException.class, () -> ProductionSettings.validate(env));
  }
}
