import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Read-only, credential-free evidence: row counts and digests, never row contents. */
public class BusinessDatabaseSnapshot {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) throw new IllegalArgumentException("Supply an evidence output path");
    String database = System.getenv().getOrDefault("DB_NAME", "campus_perks_db");
    if (!"campus_perks_db".equals(database)) throw new IllegalArgumentException("Unauthorized database");
    String url = "jdbc:mysql://127.0.0.1:3306/" + database
        + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
    try (Connection connection = DriverManager.getConnection(url,
        System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {
      connection.setReadOnly(true);
      connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      connection.setAutoCommit(false);
      List<String> tables = new ArrayList<>();
      try (PreparedStatement statement = connection.prepareStatement(
          "SELECT table_name FROM information_schema.tables WHERE table_schema=? AND table_type='BASE TABLE' ORDER BY table_name")) {
        statement.setString(1, database);
        try (ResultSet rows = statement.executeQuery()) {
          while (rows.next()) tables.add(rows.getString(1));
        }
      }
      List<String> lines = new ArrayList<>();
      lines.add("table\trows\tsha256");
      for (String table : tables) {
        if (!table.matches("[a-zA-Z0-9_]+")) throw new IllegalStateException("Unexpected table name");
        List<String> digests = new ArrayList<>();
        try (Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery("SELECT * FROM `" + table + "`")) {
          int columns = rows.getMetaData().getColumnCount();
          while (rows.next()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int column = 1; column <= columns; column++) {
              byte[] value = rows.getBytes(column);
              digest.update((value == null ? "null:" : value.length + ":").getBytes(StandardCharsets.UTF_8));
              if (value != null) digest.update(value);
              digest.update((byte) ';');
            }
            digests.add(HexFormat.of().formatHex(digest.digest()));
          }
        }
        Collections.sort(digests);
        byte[] total = MessageDigest.getInstance("SHA-256")
            .digest(String.join("\n", digests).getBytes(StandardCharsets.UTF_8));
        lines.add(table + "\t" + digests.size() + "\t" + HexFormat.of().formatHex(total));
      }
      connection.rollback();
      Path output = Path.of(args[0]);
      Files.createDirectories(output.toAbsolutePath().getParent());
      Files.write(output, lines, StandardCharsets.UTF_8);
      System.out.println("Read-only snapshot completed: " + tables.size() + " tables; no row contents exported.");
    }
  }
}
