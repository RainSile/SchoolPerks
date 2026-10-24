package com.campus.perks.community.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class CommunityRequests {
  private CommunityRequests() {}

  public record Content(
      @NotBlank @Size(max = 120) String title,
      @NotBlank @Size(max = 6000) String body,
      @NotNull @Size(max = 12) List<@NotBlank @Size(max = 64) String> imageIds) {}

  public record CreatePost(
      @NotBlank @Pattern(regexp = "[1-9][0-9]{0,17}") String schoolId,
      @Pattern(regexp = "[1-9][0-9]{0,17}") String merchantId,
      @NotNull @jakarta.validation.Valid Content content) {}

  public record UpdatePost(
      @Min(0) int version, @NotNull @jakarta.validation.Valid Content content) {}

  public record Version(@Min(0) int version) {}

  public record Decision(
      @NotNull ReviewDecision decision, @NotBlank @Size(max = 500) String reason) {}

  public enum ReviewDecision {
    APPROVED,
    REJECTED
  }

  public record HideInput(@Min(0) int version, @NotBlank @Size(max = 500) String reason) {}

  public record CommentInput(@NotBlank @Size(max = 2000) String body) {}

  public record CommentDecision(
      @Min(0) int version,
      @NotNull CommentStatus status,
      @NotBlank @Size(max = 500) String reason) {}

  public enum CommentStatus {
    APPROVED,
    REJECTED,
    HIDDEN
  }

  public record ReportInput(@NotBlank @Size(max = 500) String reason) {}

  public record Resolution(
      @Min(0) int version,
      @NotNull ReportStatus status,
      @NotBlank @Size(max = 500) String reason) {}

  public enum ReportStatus {
    RESOLVED,
    DISMISSED
  }
}
