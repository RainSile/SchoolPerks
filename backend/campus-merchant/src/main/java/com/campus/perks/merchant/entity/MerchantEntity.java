package com.campus.perks.merchant.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("campus_merchant")
public class MerchantEntity {
  @TableId(type = IdType.AUTO)
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  private Long ownerId;

  public Long getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(Long ownerId) {
    this.ownerId = ownerId;
  }

  private Long schoolId;

  public Long getSchoolId() {
    return schoolId;
  }

  public void setSchoolId(Long schoolId) {
    this.schoolId = schoolId;
  }

  private String name;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  private String category;

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  private String address;

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  private String qualificationObjectKey;

  public String getQualificationObjectKey() {
    return qualificationObjectKey;
  }

  public void setQualificationObjectKey(String qualificationObjectKey) {
    this.qualificationObjectKey = qualificationObjectKey;
  }

  private String qualificationFileId;

  public String getQualificationFileId() {
    return qualificationFileId;
  }

  public void setQualificationFileId(String qualificationFileId) {
    this.qualificationFileId = qualificationFileId;
  }

  private String description;

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  private String businessHours;

  public String getBusinessHours() {
    return businessHours;
  }

  public void setBusinessHours(String businessHours) {
    this.businessHours = businessHours;
  }

  private java.math.BigDecimal longitude;

  public java.math.BigDecimal getLongitude() {
    return longitude;
  }

  public void setLongitude(java.math.BigDecimal longitude) {
    this.longitude = longitude;
  }

  private java.math.BigDecimal latitude;

  public java.math.BigDecimal getLatitude() {
    return latitude;
  }

  public void setLatitude(java.math.BigDecimal latitude) {
    this.latitude = latitude;
  }

  private com.campus.perks.common.enums.ReviewStatus status;

  public com.campus.perks.common.enums.ReviewStatus getStatus() {
    return status;
  }

  public void setStatus(com.campus.perks.common.enums.ReviewStatus status) {
    this.status = status;
  }

  private Integer version;

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }
}
