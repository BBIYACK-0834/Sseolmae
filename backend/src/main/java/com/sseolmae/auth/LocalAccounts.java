package com.sseolmae.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class LocalAccounts implements UserDetailsService {
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;
  public LocalAccounts(JdbcTemplate db, PasswordEncoder encoder) { this.db=db; this.encoder=encoder; }
  static String normalized(String email) { return email.strip().toLowerCase(Locale.ROOT); }

  @Transactional
  public void register(String email,String password) {
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO venue_user(id,provider,subject) VALUES(?, 'local', ?)",id,id.toString());
    try {
      db.update("INSERT INTO local_credential(user_id,email,password_hash) VALUES(?,?,?)",
        id,normalized(email),encoder.encode(password));
    } catch (DuplicateKeyException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,"이 이메일로 가입할 수 없습니다.");
    }
  }

  @Override
  public UserDetails loadUserByUsername(String email) {
    return db.query("SELECT user_id,password_hash FROM local_credential WHERE email=?",
        (row,index)->User.withUsername(row.getString("user_id")).password(row.getString("password_hash"))
            .roles("USER").build(),normalized(email)).stream().findFirst()
        .orElseThrow(()->new UsernameNotFoundException("계정 정보를 확인해 주세요."));
  }
}

@RestController
class RegistrationController {
  record Registration(@NotBlank @Email @Size(max=254) String email,
                      @NotBlank @Size(min=10,max=128,message="비밀번호는 10~128자로 입력해 주세요.") String password) {}
  private final LocalAccounts accounts;
  RegistrationController(LocalAccounts accounts) { this.accounts=accounts; }

  @PostMapping("/api/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  void register(@Valid @RequestBody Registration input) { accounts.register(input.email(),input.password()); }
}
