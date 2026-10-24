package com.campus.perks.coupon.entity;

public class CouponEntity {
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }

  private Long merchantId;

  public Long getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(Long value) {
    merchantId = value;
  }

  private String title;

  public String getTitle() {
    return title;
  }

  public void setTitle(String value) {
    title = value;
  }

  private String couponType;

  public String getCouponType() {
    return couponType;
  }

  public void setCouponType(String value) {
    couponType = value;
  }

  private String rules;

  public String getRules() {
    return rules;
  }

  public void setRules(String value) {
    rules = value;
  }

  private int totalStock;

  public int getTotalStock() {
    return totalStock;
  }

  public void setTotalStock(int value) {
    totalStock = value;
  }

  private int remainingStock;

  public int getRemainingStock() {
    return remainingStock;
  }

  public void setRemainingStock(int value) {
    remainingStock = value;
  }

  private java.time.Instant validFrom;

  public java.time.Instant getValidFrom() {
    return validFrom;
  }

  public void setValidFrom(java.time.Instant value) {
    validFrom = value;
  }

  private java.time.Instant validUntil;

  public java.time.Instant getValidUntil() {
    return validUntil;
  }

  public void setValidUntil(java.time.Instant value) {
    validUntil = value;
  }

  private String description;

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    description = value;
  }

  private boolean campusOnly;

  public boolean getCampusOnly() {
    return campusOnly;
  }

  public void setCampusOnly(boolean value) {
    campusOnly = value;
  }
}
