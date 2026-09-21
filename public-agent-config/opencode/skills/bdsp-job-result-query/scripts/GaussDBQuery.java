import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GaussDBQuery {
    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Usage: <host> <port> <database> <username> <sql>; password from GAUSSDB_PASSWORD");
            System.exit(1);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String database = args[2];
        String username = args[3];
        String sql = args[4];
        String password = System.getenv("GAUSSDB_PASSWORD");
        if (password == null) {
            System.err.println("GAUSSDB_PASSWORD is not set");
            System.exit(1);
        }
        String url = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
        try {
            List<Map<String, Object>> results = executeQuery(url, username, password, sql);
            System.out.println(formatAsJSON(results));
        } catch (Exception e) {
            System.err.println("QUERY_ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private static List<Map<String, Object>> executeQuery(String url, String username, String password, String sql) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnName(i), rs.getObject(i));
                }
                results.add(row);
            }
        }
        return results;
    }

    private static String formatAsJSON(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"rows\":[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('{');
            int j = 0;
            for (Map.Entry<String, Object> entry : rows.get(i).entrySet()) {
                if (j++ > 0) sb.append(',');
                sb.append('\"').append(escapeJson(entry.getKey())).append("\":");
                sb.append(formatJsonValue(entry.getValue()));
            }
            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String formatJsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
