package com.icbc.testagent.app.config;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * 仅供 Flyway 使用的高斯数据库 JDBC 兼容数据源。
 *
 * <p>Flyway PostgreSQL 支持会在迁移结束时执行 {@code SET ROLE '账号'} 恢复连接状态。
 * 高斯数据库执行该语句需要额外的角色认证信息，因此这里仅将 Flyway 生成的精确语句
 * 转为高斯支持的 {@code RESET ROLE}，其他 SQL 原样交给真实数据源执行。</p>
 */
public final class GaussDbFlywayDataSource implements DataSource {

    private static final Pattern FLYWAY_ROLE_RESTORE_SQL = Pattern.compile(
            "(?i)^\\s*SET\\s+ROLE\\s+'(?:''|[^'])*'\\s*;?\\s*$");

    private final DataSource delegate;

    public GaussDbFlywayDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    /**
     * 将 Flyway PostgreSQL 方言的角色恢复语句转换为高斯兼容语句。
     * 非 Flyway 形式必须保持不变，避免误改迁移脚本中的业务 SQL。
     */
    static String translateRoleRestoreSql(String sql) {
        if (sql != null && FLYWAY_ROLE_RESTORE_SQL.matcher(sql).matches()) {
            return "RESET ROLE";
        }
        return sql;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrapConnection(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrapConnection(delegate.getConnection(username, password));
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }

    private static Connection wrapConnection(Connection connection) {
        InvocationHandler handler = (proxy, method, args) -> {
            Object[] invocationArgs = args == null ? null : args.clone();
            if (isStatementFactory(method) && invocationArgs != null
                    && invocationArgs.length > 0 && invocationArgs[0] instanceof String sql) {
                invocationArgs[0] = translateRoleRestoreSql(sql);
            }
            Object result = invoke(method, connection, invocationArgs);
            if (result instanceof Statement statement) {
                return wrapStatement(statement, (Connection) proxy);
            }
            return result;
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, handler);
    }

    private static Statement wrapStatement(Statement statement, Connection wrappedConnection) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("getConnection".equals(method.getName()) && (args == null || args.length == 0)) {
                return wrappedConnection;
            }
            Object[] invocationArgs = args == null ? null : args.clone();
            if (isSqlExecutionMethod(method) && invocationArgs != null
                    && invocationArgs.length > 0 && invocationArgs[0] instanceof String sql) {
                invocationArgs[0] = translateRoleRestoreSql(sql);
            }
            return invoke(method, statement, invocationArgs);
        };
        return (Statement) Proxy.newProxyInstance(
                statementInterfaceLoader(statement), statementInterfaces(statement), handler);
    }

    private static ClassLoader statementInterfaceLoader(Statement statement) {
        ClassLoader classLoader = statement.getClass().getClassLoader();
        return classLoader == null ? Statement.class.getClassLoader() : classLoader;
    }

    private static Class<?>[] statementInterfaces(Statement statement) {
        if (statement instanceof CallableStatement) {
            return new Class<?>[]{CallableStatement.class};
        }
        if (statement instanceof PreparedStatement) {
            return new Class<?>[]{PreparedStatement.class};
        }
        return new Class<?>[]{Statement.class};
    }

    private static boolean isStatementFactory(Method method) {
        return ("prepareStatement".equals(method.getName()) || "prepareCall".equals(method.getName()))
                && method.getParameterCount() > 0
                && method.getParameterTypes()[0] == String.class;
    }

    private static boolean isSqlExecutionMethod(Method method) {
        String methodName = method.getName();
        return "execute".equals(methodName)
                || "executeUpdate".equals(methodName)
                || "executeLargeUpdate".equals(methodName)
                || "addBatch".equals(methodName);
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException invocationException) {
            throw invocationException.getCause();
        }
    }
}
