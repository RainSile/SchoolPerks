package com.campus.perks.assistant.mapper;

import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class AiUsageMapper {
  private final JdbcTemplate db;

  public AiUsageMapper(JdbcTemplate db) {
    this.db = db;
  }

  public boolean reserve(String user, LocalDate day, String kind, int maximum) {
    db.update(
        "INSERT INTO campus_ai_usage(user_id,usage_day,kind,call_count) VALUES(?,?,?,0) ON"
            + " DUPLICATE KEY UPDATE user_id=user_id",
        user,
        day,
        kind);
    return db.update(
            "UPDATE campus_ai_usage SET call_count=call_count+1 WHERE user_id=? AND usage_day=? AND"
                + " kind=? AND call_count<?",
            user,
            day,
            kind,
            maximum)
        == 1;
  }

  public int used(String user, LocalDate day, String kind) {
    var rows =
        db.queryForList(
            "SELECT call_count FROM campus_ai_usage WHERE user_id=? AND usage_day=? AND kind=?",
            Integer.class,
            user,
            day,
            kind);
    return rows.isEmpty() ? 0 : rows.get(0);
  }
}
