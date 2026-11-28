package com.sseolmae.ops;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Staff {
  private final Set<UUID> ids = new HashSet<>();
  public Staff(@Value("${STAFF_USER_IDS:}") String values) {
    for (String value : values.split(",")) if (!value.isBlank()) ids.add(UUID.fromString(value.strip()));
  }
  public boolean includes(UUID id) { return ids.contains(id); }
}
