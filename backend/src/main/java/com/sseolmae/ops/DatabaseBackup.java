package com.sseolmae.ops;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="venue.backup-enabled",havingValue="true")
public class DatabaseBackup {
  private final JdbcTemplate db;
  private final Path directory;
  DatabaseBackup(JdbcTemplate db,@Value("${venue.backup-dir}") String directory) {
    this.db=db;
    this.directory=Path.of(directory);
    if (!this.directory.isAbsolute() || !Files.isDirectory(this.directory) || !Files.isWritable(this.directory))
      throw new IllegalStateException("백업 경로에 쓰기 가능한 외부 볼륨을 먼저 연결하세요.");
  }

  @Scheduled(cron="${venue.backup-cron:0 */15 * * * *}",zone="UTC")
  public void save() throws IOException {
    String timestamp=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
    Path temp=Files.createTempFile(directory,".backup-",".part");
    try {
      db.execute("BACKUP TO '" + temp.toString().replace("'","''") + "'");
      Files.move(temp,directory.resolve(timestamp+"-"+UUID.randomUUID()+".zip"),StandardCopyOption.ATOMIC_MOVE);
    } finally { Files.deleteIfExists(temp); }
  }
}
