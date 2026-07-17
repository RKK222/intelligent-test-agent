package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterRepository;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 通用参数存量 JDBC Repository，仅保留给旧集成测试直接构造使用。
 *
 * <p>生产环境的 CommonParameterRepository Bean 已迁移到 MyBatis 实现；
 * 后续通用参数 SQL 变更必须写入 MyBatis XML mapper。
 */
public class JdbcCommonParameterRepository extends JdbcRepositorySupport implements CommonParameterRepository {

    private static final String SELECT_COLUMNS = """
            parameter_id, parameter_english, parameter_chinese, parameter_value, platform, editable, created_at, updated_at
            """;

    private final JdbcClient jdbcClient;

    private final RowMapper<CommonParameter> mapper = (rs, rowNum) -> new CommonParameter(
            rs.getString("parameter_id"),
            rs.getString("parameter_english"),
            rs.getString("parameter_chinese"),
            rs.getString("parameter_value"),
            ParameterPlatform.fromValue(rs.getString("platform")),
            rs.getBoolean("editable"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    public JdbcCommonParameterRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<CommonParameter> findByEnglishNameAndPlatform(String englishName, ParameterPlatform platform) {
        return jdbcClient.sql("""
                        select %s from common_parameters
                        where parameter_english = :englishName and platform = :platform
                        """.formatted(SELECT_COLUMNS))
                .param("englishName", englishName)
                .param("platform", platform.value())
                .query(mapper)
                .optional();
    }

    @Override
    public List<CommonParameter> findAll() {
        return jdbcClient.sql("""
                        select %s from common_parameters
                        order by parameter_english, platform
                        """.formatted(SELECT_COLUMNS))
                .query(mapper)
                .list();
    }

    @Override
    public Optional<CommonParameter> findByParameterId(String parameterId) {
        return jdbcClient.sql("""
                        select %s from common_parameters
                        where parameter_id = :parameterId
                        """.formatted(SELECT_COLUMNS))
                .param("parameterId", parameterId)
                .query(mapper)
                .optional();
    }

    @Override
    public int updateValue(String parameterId, String newValue, Instant updatedAt) {
        return jdbcClient.sql("""
                        update common_parameters
                        set parameter_value = :value, updated_at = :updatedAt
                        where parameter_id = :parameterId
                        """)
                .param("value", newValue)
                .param("updatedAt", timestamp(updatedAt))
                .param("parameterId", parameterId)
                .update();
    }
}
