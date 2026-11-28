package com.sseolmae;

import java.net.URI;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

final class ProductionSettings {
  private ProductionSettings() {}

  static void validate(Environment env) {
    if (!env.acceptsProfiles(Profiles.of("prod"))) return;
    require(!env.acceptsProfiles(Profiles.of("demo")), "prod와 demo를 동시에 사용할 수 없습니다.");
    String origin = env.getProperty("venue.origin", "");
    URI uri;
    try { uri = URI.create(origin); }
    catch (IllegalArgumentException invalid) { throw new IllegalStateException("APP_ORIGIN 형식을 확인하세요."); }
    require("https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
        && uri.getRawPath().isEmpty() && uri.getQuery() == null && uri.getFragment() == null,
        "APP_ORIGIN에 경로 없는 HTTPS 도메인을 설정하세요.");
    require(env.getProperty("server.servlet.session.cookie.secure", Boolean.class, false),
        "운영 세션에는 Secure 쿠키가 필요합니다.");
    String db = env.getProperty("spring.datasource.url", "");
    require(db.startsWith("jdbc:h2:file:/") && !db.contains(";"), "DB_URL에 영구 볼륨의 절대 H2 파일 경로를 설정하세요.");
    require(!env.getProperty("spring.datasource.password", "").isBlank(), "DB_PASSWORD를 설정하세요.");
    String client = env.getProperty("venue.toss-client", "");
    String secret = env.getProperty("venue.toss-secret", "");
    require((client.isBlank() && secret.isBlank()) ||
        (client.startsWith("live_ck_") && secret.startsWith("live_sk_")),
        "실결제는 라이브 API 개별 연동 키 쌍이 필요합니다. 판매 전에는 두 키를 모두 비워 두세요.");
    for (String provider : new String[]{"GOOGLE","KAKAO","NAVER"}) {
      require(env.getProperty(provider + "_CLIENT_ID","").isBlank() ==
          env.getProperty(provider + "_CLIENT_SECRET","").isBlank(), provider + " 로그인 키 쌍을 확인하세요.");
    }
  }

  private static void require(boolean valid, String message) {
    if (!valid) throw new IllegalStateException(message);
  }
}
