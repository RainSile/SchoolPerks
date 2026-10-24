package com.campus.perks.merchant.mapper;

import com.campus.perks.merchant.dto.LocationRequests.*;
import com.campus.perks.merchant.vo.LocationViews.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class LocationMapper {
  private final JdbcTemplate jdbc;

  public LocationMapper(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Location location(long merchant, boolean lock) {
    var rows =
        jdbc.query(
            "SELECT * FROM campus_merchant_location WHERE merchant_id=?"
                + (lock ? " FOR UPDATE" : ""),
            (r, n) ->
                new Location(
                    r.getString("merchant_id"),
                    r.getBigDecimal("longitude"),
                    r.getBigDecimal("latitude"),
                    r.getString("coordinate_system"),
                    r.getInt("version"),
                    r.getTimestamp("updated_at").toInstant()),
            merchant);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public void save(long merchant, Input input, Instant now) {
    jdbc.update(
        "INSERT INTO"
            + " campus_merchant_location(merchant_id,longitude,latitude,coordinate_system,version,updated_at)"
            + " VALUES(?,?,?,'WGS84',1,?) ON DUPLICATE KEY UPDATE"
            + " longitude=VALUES(longitude),latitude=VALUES(latitude),version=version+1,updated_at=VALUES(updated_at)",
        merchant,
        input.longitude(),
        input.latitude(),
        Timestamp.from(now));
  }

  private static final String NEARBY =
      "SELECT"
          + " m.id,m.name,m.category,m.address,l.longitude,l.latitude,ST_Distance_Sphere(POINT(l.longitude,l.latitude),POINT(?,?))"
          + " AS distance FROM campus_merchant m JOIN campus_merchant_location l ON"
          + " l.merchant_id=m.id WHERE m.status='APPROVED' AND l.coordinate_system='WGS84' AND (?"
          + " IS NULL OR m.category=?) AND (? IS NULL OR m.school_id=?)";

  private Object[] args(NearbyQuery q) {
    var lon = q.getLongitude();
    if (lon.compareTo(java.math.BigDecimal.valueOf(-180)) == 0)
      lon = java.math.BigDecimal.valueOf(180);
    return new Object[] {
      lon,
      q.getLatitude(),
      q.getCategory(),
      q.getCategory(),
      q.getSchoolId(),
      q.getSchoolId(),
      q.getRadiusMeters()
    };
  }

  public List<Nearby> nearby(NearbyQuery q) {
    var args = new ArrayList<>(Arrays.asList(args(q)));
    args.add(q.getSize());
    args.add((long) (q.getPage() - 1) * q.getSize());
    return jdbc.query(
        "SELECT * FROM ("
            + NEARBY
            + ") distances WHERE distance<=? ORDER BY distance,id LIMIT ? OFFSET ?",
        (r, n) ->
            new Nearby(
                r.getString("id"),
                r.getString("name"),
                r.getString("category"),
                r.getString("address"),
                r.getBigDecimal("longitude"),
                r.getBigDecimal("latitude"),
                "WGS84",
                Math.round(r.getDouble("distance"))),
        args.toArray());
  }

  public long count(NearbyQuery q) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM (" + NEARBY + ") distances WHERE distance<=?", Long.class, args(q));
  }
}
