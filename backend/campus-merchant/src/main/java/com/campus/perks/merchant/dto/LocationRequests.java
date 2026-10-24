package com.campus.perks.merchant.dto;

import com.campus.perks.common.api.PageQuery;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class LocationRequests {
  private LocationRequests() {}

  public record Input(
      @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7)
          BigDecimal longitude,
      @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 2, fraction = 7)
          BigDecimal latitude,
      @NotBlank String coordinateSystem,
      @Min(0) int version) {}

  public static class NearbyQuery extends PageQuery {
    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    private BigDecimal longitude;

    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    private BigDecimal latitude;

    @NotBlank private String coordinateSystem;

    @Min(1)
    private int radiusMeters = 5000;

    @Size(max = 64)
    private String category;

    @Pattern(regexp = "[1-9][0-9]{0,17}")
    private String schoolId;

    public BigDecimal getLongitude() {
      return longitude;
    }

    public void setLongitude(BigDecimal v) {
      longitude = v;
    }

    public BigDecimal getLatitude() {
      return latitude;
    }

    public void setLatitude(BigDecimal v) {
      latitude = v;
    }

    public String getCoordinateSystem() {
      return coordinateSystem;
    }

    public void setCoordinateSystem(String v) {
      coordinateSystem = v;
    }

    public int getRadiusMeters() {
      return radiusMeters;
    }

    public void setRadiusMeters(int v) {
      radiusMeters = v;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String v) {
      category = v;
    }

    public String getSchoolId() {
      return schoolId;
    }

    public void setSchoolId(String v) {
      schoolId = v;
    }
  }
}
