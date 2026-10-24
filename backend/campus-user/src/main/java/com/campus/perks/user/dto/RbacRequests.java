package com.campus.perks.user.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class RbacRequests {
  private RbacRequests() {}

  public record RoleInput(
      @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,63}") String code,
      @NotBlank @Size(max = 64) String name,
      @NotNull Portal accountType,
      @NotNull @Size(max = 50) List<@NotBlank @Size(max = 120) String> permissions) {}

  public record RoleUpdate(
      @Min(0) int version,
      @NotBlank @Size(max = 64) String name,
      @NotNull @Size(max = 50) List<@NotBlank @Size(max = 120) String> permissions) {}

  public record AccountUpdate(
      @Min(0) int version, @NotNull Portal accountType, @NotNull Status status) {}

  public record Grants(
      @Min(0) int version,
      @NotNull @Size(max = 20) List<@Pattern(regexp = "[1-9][0-9]{0,17}") String> roleIds) {}

  public enum Portal {
    USER,
    MERCHANT,
    ADMIN
  }

  public enum Status {
    ACTIVE,
    DISABLED
  }
}
