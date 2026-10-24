package com.campus.perks.coupon.mapper;

import com.campus.perks.coupon.dto.CouponQuery;
import com.campus.perks.coupon.entity.CatalogRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

/**
 * Explicit read-only public projection from the migration-owned catalog view. No foreign writes.
 */
@Mapper
public interface CouponCatalogMapper {
  String COLUMNS =
      "SELECT"
          + " id,merchant_id,merchant_name,school_id,title,coupon_type,rules,remaining_stock,valid_from,valid_until,campus_only,description"
          + " FROM campus_coupon_catalog ";
  String FILTER =
      " WHERE status='PUBLISHED' AND review_status='APPROVED' AND published_at IS NOT NULL AND"
          + " merchant_status='APPROVED' AND valid_until > #{now} <if test='q.keyword != null and"
          + " q.keyword != \"\"'> AND LOCATE(#{q.keyword},title)>0 </if><if test='q.schoolId !="
          + " null'> AND school_id=#{q.schoolId} </if><if test='q.merchantId != null'> AND"
          + " merchant_id=#{q.merchantId} </if>";

  @Select(
      "<script>" + COLUMNS + FILTER + " ORDER BY id DESC LIMIT #{q.size} OFFSET #{offset}</script>")
  List<CatalogRow> list(
      @Param("q") CouponQuery q, @Param("now") Instant now, @Param("offset") long offset);

  @Select("<script>SELECT COUNT(*) FROM campus_coupon_catalog " + FILTER + "</script>")
  long count(@Param("q") CouponQuery q, @Param("now") Instant now);

  @Select(
      COLUMNS
          + "WHERE id=#{id} AND status='PUBLISHED' AND review_status='APPROVED' AND published_at IS"
          + " NOT NULL AND merchant_status='APPROVED' AND valid_until>#{now}")
  CatalogRow publicDetail(@Param("id") long id, @Param("now") Instant now);

  @Select(COLUMNS + "WHERE id=#{id}")
  CatalogRow description(long id);
}
