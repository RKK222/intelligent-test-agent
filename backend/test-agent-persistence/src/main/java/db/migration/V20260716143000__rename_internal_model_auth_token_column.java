package db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 把内部模型代理设置表中的历史鉴权列统一为企业中性名称。
 *
 * <p>迁移按列位置识别历史列，避免在源码中继续保留已废弃的机构标识；新建数据库已经使用目标列名时直接跳过。
 */
public final class V20260716143000__rename_internal_model_auth_token_column extends BaseJavaMigration {

    private static final String TABLE_NAME = "internal_model_proxy_settings";
    private static final String TARGET_COLUMN = "enterprise_openai_auth_token";

    /**
     * 在 PostgreSQL 与 H2 PostgreSQL 模式中查找第二列并执行兼容重命名。
     */
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String schema = connection.getSchema();
        String sourceColumn = findSourceColumn(connection, schema);
        if (TARGET_COLUMN.equalsIgnoreCase(sourceColumn)) {
            return;
        }
        if (sourceColumn == null) {
            throw new FlywayException("无法识别内部模型代理设置表的历史鉴权列");
        }

        String renameSql = "alter table " + quoteIdentifier(schema) + "." + quoteIdentifier(TABLE_NAME)
                + " rename column " + quoteIdentifier(sourceColumn) + " to " + quoteIdentifier(TARGET_COLUMN);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(renameSql);
        }
    }

    /**
     * 历史表的鉴权 token 固定位于第二列；目标列已经存在时优先返回目标列。
     */
    private String findSourceColumn(Connection connection, String schema) throws Exception {
        String secondColumn = null;
        // JDBC metadata 的列序号遵循标准定义，避免 H2 与 PostgreSQL 的 information_schema 大小写差异。
        try (ResultSet resultSet = connection.getMetaData().getColumns(null, null, null, null)) {
            while (resultSet.next()) {
                String columnSchema = resultSet.getString(2);
                String tableName = resultSet.getString(3);
                if (!schema.equalsIgnoreCase(columnSchema) || !TABLE_NAME.equalsIgnoreCase(tableName)) {
                    continue;
                }
                String columnName = resultSet.getString(4);
                if (TARGET_COLUMN.equalsIgnoreCase(columnName)) {
                    return TARGET_COLUMN;
                }
                if (resultSet.getInt(17) == 2) {
                    secondColumn = columnName;
                }
            }
        }
        return secondColumn;
    }

    /**
     * 对迁移内部识别出的 schema、表名和列名进行 SQL 标识符转义。
     */
    private String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new FlywayException("数据库 schema 不能为空");
        }
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
