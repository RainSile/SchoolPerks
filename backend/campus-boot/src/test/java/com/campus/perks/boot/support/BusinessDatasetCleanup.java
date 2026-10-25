package com.campus.perks.boot.support;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Deletes only descendants of this journey's exact root IDs. No timestamps, global deletes,
 * disabled foreign keys, schema changes or shared permission rows. */
public final class BusinessDatasetCleanup {
  private record Key(List<Object> values) {}
  private record ForeignKey(String table, String column, String parent, String parentColumn, boolean nullable) {}
  private final JdbcTemplate jdbc;
  private final Map<String, List<String>> primaryKeys = new LinkedHashMap<>();
  private final List<ForeignKey> foreignKeys = new ArrayList<>();
  private final Map<String, Set<Key>> scoped = new LinkedHashMap<>();

  public BusinessDatasetCleanup(DataSource source, Map<String, List<Long>> roots) throws Exception {
    jdbc = new JdbcTemplate(source);
    try (Connection connection = source.getConnection()) {
      if (!"campus_perks_db".equals(connection.getCatalog())) throw new IllegalStateException("Unauthorized database");
      DatabaseMetaData metadata = connection.getMetaData();
      List<String> tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema='campus_perks_db' AND table_type='BASE TABLE' ORDER BY table_name", String.class);
      for (String table : tables) {
        Map<Short, String> keys = new TreeMap<>();
        try (ResultSet rows = metadata.getPrimaryKeys("campus_perks_db", null, table)) {
          while (rows.next()) keys.put(rows.getShort("KEY_SEQ"), rows.getString("COLUMN_NAME"));
        }
        primaryKeys.put(table, new ArrayList<>(keys.values()));
        Map<String, Boolean> nullable = new HashMap<>();
        try (ResultSet rows = metadata.getColumns("campus_perks_db", null, table, null)) {
          while (rows.next()) nullable.put(rows.getString("COLUMN_NAME"), rows.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
        }
        try (ResultSet rows = metadata.getImportedKeys("campus_perks_db", null, table)) {
          while (rows.next()) {
            if (!"campus_perks_db".equals(rows.getString("PKTABLE_CAT"))) throw new IllegalStateException("Cross-database foreign key");
            if (rows.getShort("KEY_SEQ") != 1) throw new IllegalStateException("Composite foreign key needs explicit cleanup");
            String column = rows.getString("FKCOLUMN_NAME");
            foreignKeys.add(new ForeignKey(table, column, rows.getString("PKTABLE_NAME"), rows.getString("PKCOLUMN_NAME"), nullable.getOrDefault(column, false)));
          }
        }
      }
    }
    for (var root : roots.entrySet()) {
      if (!List.of("campus_user", "campus_school", "campus_role").contains(root.getKey())
          || primaryKeys.get(root.getKey()).size() != 1) throw new IllegalArgumentException("Invalid cleanup root");
      Set<Key> keys = new LinkedHashSet<>();
      for (Long id : root.getValue()) keys.add(new Key(List.of(id)));
      scoped.put(root.getKey(), keys);
    }
  }

  private static String quoted(String identifier) {
    if (!identifier.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Invalid metadata identifier");
    return "`" + identifier + "`";
  }

  private static String placeholders(int count) { return String.join(",", Collections.nCopies(count, "?")); }

  private List<Object> ids(String table) {
    if (primaryKeys.get(table).size() != 1) return List.of();
    return scoped.getOrDefault(table, Set.of()).stream().map(key -> key.values().get(0)).toList();
  }

  public void clean() {
    Set<String> roots = Set.copyOf(scoped.keySet());
    boolean changed;
    do {
      changed = false;
      for (ForeignKey fk : foreignKeys) {
        if (roots.contains(fk.table())) continue;
        List<Object> parents = ids(fk.parent());
        if (parents.isEmpty()) continue;
        List<String> keys = primaryKeys.get(fk.table());
        if (keys.isEmpty()) throw new IllegalStateException("Scoped child has no primary key");
        if (!primaryKeys.get(fk.parent()).equals(List.of(fk.parentColumn()))) throw new IllegalStateException("Non-primary foreign key needs explicit cleanup");
        String sql = "SELECT " + String.join(",", keys.stream().map(BusinessDatasetCleanup::quoted).toList()) + " FROM " + quoted(fk.table()) + " WHERE " + quoted(fk.column()) + " IN (" + placeholders(parents.size()) + ")";
        List<Key> children = jdbc.query(sql, (row, number) -> {
          List<Object> values = new ArrayList<>();
          for (int column = 1; column <= keys.size(); column++) values.add(row.getObject(column));
          return new Key(values);
        }, parents.toArray());
        Set<Key> target = scoped.computeIfAbsent(fk.table(), ignored -> new LinkedHashSet<>());
        if (target.addAll(children)) changed = true;
        if (target.size() > 500) throw new IllegalStateException("Unexpectedly large fixture cleanup scope");
      }
    } while (changed);

    cleanDerivedRows();
    for (ForeignKey fk : foreignKeys) {
      if (!fk.nullable() || ids(fk.parent()).isEmpty()) continue;
      List<Object> parents = ids(fk.parent());
      for (Key key : scoped.getOrDefault(fk.table(), Set.of())) {
        List<Object> arguments = new ArrayList<>(key.values());
        arguments.addAll(parents);
        jdbc.update("UPDATE " + quoted(fk.table()) + " SET " + quoted(fk.column()) + "=NULL WHERE " + where(fk.table()) + " AND " + quoted(fk.column()) + " IN (" + placeholders(parents.size()) + ")", arguments.toArray());
      }
    }
    Set<String> pending = new LinkedHashSet<>(scoped.keySet());
    while (!pending.isEmpty()) {
      boolean progress = false;
      for (String table : List.copyOf(pending)) {
        boolean hasChild = foreignKeys.stream().anyMatch(fk -> fk.parent().equals(table) && !fk.table().equals(table) && pending.contains(fk.table()) && !scoped.getOrDefault(fk.table(), Set.of()).isEmpty() && !fk.nullable());
        if (hasChild) continue;
        for (Key key : scoped.get(table)) jdbc.update("DELETE FROM " + quoted(table) + " WHERE " + where(table), key.values().toArray());
        pending.remove(table);
        progress = true;
      }
      if (!progress) throw new IllegalStateException("Unresolved scoped foreign-key cycle");
    }
  }

  private String where(String table) {
    return String.join(" AND ", primaryKeys.get(table).stream().map(column -> quoted(column) + "=?").toList());
  }

  private void cleanDerivedRows() {
    List<Object> users = ids("campus_user");
    List<String> resources = new ArrayList<>();
    for (var entry : Map.of("MERCHANT", "campus_merchant", "COUPON", "campus_coupon", "OFFER", "campus_trade_offer", "POST", "campus_post").entrySet()) {
      for (Object id : ids(entry.getValue())) {
        resources.add("SEARCH:" + entry.getKey() + ":" + id);
        jdbc.update("DELETE FROM campus_search_document WHERE resource_type=? AND resource_id=?", entry.getKey(), id);
      }
    }
    String predicate = "JSON_UNQUOTE(JSON_EXTRACT(payload,'$.userId')) IN (" + placeholders(users.size()) + ")";
    List<Object> arguments = new ArrayList<>(users);
    if (!resources.isEmpty()) { predicate += " OR (event_type='SEARCH_RESOURCE_CHANGED' AND aggregate_id IN (" + placeholders(resources.size()) + "))"; arguments.addAll(resources); }
    List<String> events = jdbc.queryForList("SELECT id FROM campus_outbox_event WHERE " + predicate, String.class, arguments.toArray());
    for (String event : events) {
      jdbc.update("DELETE FROM campus_consumed_event WHERE event_id=?", event);
      jdbc.update("DELETE FROM campus_outbox_event WHERE id=?", event);
    }
    jdbc.update("DELETE FROM campus_operation_audit WHERE actor_id IN (" + placeholders(users.size()) + ")", users.toArray());
  }
}
