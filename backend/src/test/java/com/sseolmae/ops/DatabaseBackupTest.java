package com.sseolmae.ops;

import java.nio.file.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseBackupTest {
  @TempDir Path dir;
  @Test void restoresCommittedData() throws Exception {
    var original=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:file:"+dir.resolve("source"),"sa",""));
    original.execute("CREATE TABLE marker(id INT)");
    original.update("INSERT INTO marker VALUES(37)");
    new DatabaseBackup(original,dir.toString()).save();
    Path zip;
    try(var files=Files.list(dir)){zip=files.filter(p->p.toString().endsWith(".zip")).findFirst().orElseThrow();}
    Path restored=Files.createDirectory(dir.resolve("restored"));
    try(var archive=new ZipFile(zip.toFile());var data=archive.getInputStream(archive.getEntry("source.mv.db"))){
      Files.copy(data,restored.resolve("source.mv.db"));
    }
    var copy=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:file:"+restored.resolve("source")+";IFEXISTS=TRUE","sa",""));
    assertEquals(37,copy.queryForObject("SELECT id FROM marker",Integer.class));
  }
}
