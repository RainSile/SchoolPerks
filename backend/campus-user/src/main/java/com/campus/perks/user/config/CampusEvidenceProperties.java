package com.campus.perks.user.config;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
@Validated @ConfigurationProperties(prefix="campus.campus-evidence")
public record CampusEvidenceProperties(Duration retention, @Min(1) int cleanupBatchSize, boolean cleanupEnabled) {
  public CampusEvidenceProperties {
    if(retention==null || retention.compareTo(Duration.ofDays(1))<0)
      throw new IllegalArgumentException("Student-card retention must be at least one day");
  }
}
