package com.campus.perks.merchant.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.*;
import com.campus.perks.merchant.dto.LocationRequests.*;
import com.campus.perks.merchant.mapper.LocationMapper;
import com.campus.perks.merchant.service.LocationService;
import com.campus.perks.merchant.vo.LocationViews.*;
import com.campus.perks.security.service.CurrentActor;
import java.time.Clock;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
@Transactional
public class LocationServiceImpl implements LocationService {
  private final LocationMapper db;
  private final MerchantAccessPort merchants;
  private final AccountDirectoryPort accounts;
  private final AuditPort audit;
  private final Clock clock;
  private final int maxRadius;

  public LocationServiceImpl(
      LocationMapper db,
      MerchantAccessPort merchants,
      AccountDirectoryPort accounts,
      AuditPort audit,
      Clock clock,
      @Value("${campus.location.max-radius-meters}") int maxRadius) {
    this.db = db;
    this.merchants = merchants;
    this.accounts = accounts;
    this.audit = audit;
    this.clock = clock;
    this.maxRadius = maxRadius;
    if (maxRadius < 1) throw new IllegalArgumentException("Invalid location radius");
  }

  private void crs(String value) {
    if (!"WGS84".equals(value))
      throw new ApplicationException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "COORDINATE_SYSTEM_UNSUPPORTED",
          "当前仅接收明确的WGS84坐标；地图GCJ02坐标需通过供应商转换后使用");
  }

  public Location location(String id) {
    merchants.requireMerchantMember(id);
    var row = db.location(Long.parseLong(id), false);
    if (row == null)
      throw new ApplicationException(HttpStatus.NOT_FOUND, "LOCATION_NOT_SET", "门店尚未设置明确坐标系的位置");
    return row;
  }

  public Location save(String id, Input input) {
    crs(input.coordinateSystem());
    accounts.lockActiveUser(CurrentActor.id());
    merchants.requirePromotionPublisher(id);
    var row = db.location(Long.parseLong(id), true);
    if ((row == null ? 0 : row.version()) != input.version())
      throw new ApplicationException(HttpStatus.CONFLICT, "LOCATION_VERSION_CONFLICT", "位置已变化，请刷新");
    var lon =
        input.longitude().compareTo(java.math.BigDecimal.valueOf(-180)) == 0
            ? java.math.BigDecimal.valueOf(180)
            : input.longitude();
    db.save(
        Long.parseLong(id),
        new Input(lon, input.latitude(), input.coordinateSystem(), input.version()),
        clock.instant());
    audit.record(
        CurrentActor.id(),
        "MERCHANT_LOCATION_UPDATED",
        "MERCHANT",
        id,
        Map.of("coordinateSystem", "WGS84"));
    return db.location(Long.parseLong(id), true);
  }

  public PageResponse<Nearby> nearby(NearbyQuery q) {
    crs(q.getCoordinateSystem());
    if (q.getRadiusMeters() > maxRadius)
      throw new ApplicationException(
          HttpStatus.UNPROCESSABLE_ENTITY, "LOCATION_RADIUS_EXCEEDED", "搜索半径超过配置上限");
    return new PageResponse<>(db.nearby(q), db.count(q), q.getPage(), q.getSize());
  }
}
