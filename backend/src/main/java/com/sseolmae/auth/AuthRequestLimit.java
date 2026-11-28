package com.sseolmae.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

/** Single-server fixed windows. Remote addresses come from the servlet container. */
class AuthRequestLimit extends OncePerRequestFilter {
  private record Window(long until, int attempts) {}
  private final Map<String, Window> limits = new HashMap<>();
  private long nextCleanup;

  private synchronized boolean admit(String key, int max, long now) {
    if (now >= nextCleanup) {
      limits.values().removeIf(w -> w.until() <= now);
      nextCleanup = now + 60_000;
    }
    Window w = limits.get(key);
    if (w == null && limits.size() >= 50_000) return false;
    if (w == null || now >= w.until()) w = new Window(now + 60_000, 0);
    if (w.attempts() >= max) return false;
    limits.put(key, new Window(w.until(), w.attempts() + 1));
    return true;
  }

  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String path = req.getServletPath();
    boolean login = path.equals("/api/auth/login");
    if (req.getMethod().equals("POST") && (login || path.equals("/api/auth/register"))) {
      long now = System.currentTimeMillis();
      String email = req.getParameter("email");
      if (!admit(path + req.getRemoteAddr(), login ? 30 : 10, now)
          || (login && email != null && (email.length() > 254 || !admit("account:" + LocalAccounts.normalized(email), 10, now)))) {
        res.setStatus(429);
        res.setHeader("Retry-After", "60");
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"detail\":\"요청이 많습니다. 1분 뒤 다시 시도해 주세요.\"}");
        return;
      }
    }
    chain.doFilter(req,res);
  }
}
