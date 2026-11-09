package com.campus.perks.boot;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
/** D-layer metadata fixture only. Real object bytes are exercised by the MinIO live suite. */
final class StudentCardFixture {
  private StudentCardFixture() {}
  static String ready(JdbcTemplate jdbc,long owner) {
    String id=UUID.randomUUID().toString();
    jdbc.update("INSERT INTO campus_file_object(id,owner_id,purpose,original_name,content_type,declared_size,staging_key,immutable_key,status,created_at,expires_at,completed_at) VALUES(?,?,'STUDENT_CARD','synthetic-student-card.png','image/png',64,?,?,'READY',UTC_TIMESTAMP(3),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 5 MINUTE),UTC_TIMESTAMP(3))",
      id,owner,"test/student-cards/pending/"+id,"test/student-cards/sealed/"+id);
    return id;
  }
}
