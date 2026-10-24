package com.campus.perks.coupon.serviceImpl;

import com.campus.perks.coupon.mapper.CouponCatalogMapper;
import com.campus.perks.integration.port.PromotionFactsPort;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class PromotionFactsService implements PromotionFactsPort {
  private final CouponCatalogMapper db;
  private final Clock clock;

  public PromotionFactsService(CouponCatalogMapper db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  public Coupon publicCoupon(String id) {
    if (id == null || !id.matches("[1-9][0-9]{0,17}")) return null;
    var c = db.publicDetail(Long.parseLong(id), clock.instant());
    return c == null
        ? null
        : new Coupon(
            c.id().toString(),
            c.merchantId().toString(),
            c.title(),
            c.couponType(),
            c.rules(),
            c.validFrom(),
            c.validUntil(),
            c.campusOnly(),
            c.description());
  }
}
