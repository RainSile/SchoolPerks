package com.campus.perks.coupon.dto;

import com.campus.perks.common.api.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "公开有效优惠查询；未来开始的已发布活动也可浏览")
public class CouponQuery extends PageQuery {
  @Size(max = 100)
  private String keyword;

  @Pattern(regexp = "[1-9][0-9]{0,17}")
  private String schoolId;

  @Pattern(regexp = "[1-9][0-9]{0,17}")
  private String merchantId;

  public String getKeyword() {
    return keyword;
  }

  public void setKeyword(String value) {
    keyword = value;
  }

  public String getSchoolId() {
    return schoolId;
  }

  public void setSchoolId(String value) {
    schoolId = value;
  }

  public String getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(String value) {
    merchantId = value;
  }
}
