package com.icbc.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class GaussDbFlywayDataSourceTest {

    @Test
    void translatesFlywayRoleRestoreStatementWhenItIsExecuted() throws Exception {
        AtomicReference<String> executedSql = new AtomicReference<>();
        Statement delegateStatement = proxy(Statement.class, (method, args) -> {
            if ("execute".equals(method.getName())) {
                executedSql.set((String) args[0]);
                return false;
            }
            return defaultValue(method.getReturnType());
        });
        Connection delegateConnection = proxy(Connection.class, (method, args) -> {
            if ("createStatement".equals(method.getName())) {
                return delegateStatement;
            }
            return defaultValue(method.getReturnType());
        });
        DataSource delegateDataSource = proxy(DataSource.class, (method, args) -> {
            if ("getConnection".equals(method.getName())) {
                return delegateConnection;
            }
            return defaultValue(method.getReturnType());
        });

        try (Connection connection = new GaussDbFlywayDataSource(delegateDataSource).getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE 'testagent'");
        }

        assertThat(executedSql).hasValue("RESET ROLE");
    }

    @Test
    void leavesNonFlywayRoleStatementsUnchanged() {
        assertThat(GaussDbFlywayDataSource.translateRoleRestoreSql(
                "SET ROLE testagent PASSWORD 'secret'"))
                .isEqualTo("SET ROLE testagent PASSWORD 'secret'");
        assertThat(GaussDbFlywayDataSource.translateRoleRestoreSql(
                "SET ROLE \"testagent\""))
                .isEqualTo("SET ROLE \"testagent\"");
        assertThat(GaussDbFlywayDataSource.translateRoleRestoreSql("SELECT 1"))
                .isEqualTo("SELECT 1");
    }

    private static <T> T proxy(Class<T> type, SqlInvocation invocation) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) ->
                        invocation.invoke(method, args)));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    @FunctionalInterface
    private interface SqlInvocation {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}
