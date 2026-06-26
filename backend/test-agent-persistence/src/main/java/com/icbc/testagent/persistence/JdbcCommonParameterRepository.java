package com.icbc.testagent.persistence;

import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterRepository;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 通用参数 JDBC Repository，按参数英文名和平台提供只读查询。
 */
@Repository
public class JdbcCommonParameterRepository extends JdbcRepositorySupport implements CommonParameterRepository {

    private final JdbcClient jdbcClient;

    private final RowMapper<CommonParameter> mapper = (rs, rowNum) -> new CommonParameter(
            rs.getString("parameter_id"),
            rs.getString("parameter_english"),
            rs.getString("parameter_chinese"),
            rs.getString("parameter_value"),
            ParameterPlatform.fromValue(rs.getString("platform")),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    public JdbcCommonParameterRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<CommonParameter> findByEnglishNameAndPlatform(String englishName, ParameterPlatform platform) {
        return jdbcClient.sql("""
                        select parameter_id, parameter_english, parameter_chinese, parameter_value, platform, created_at, updated_at
                        from common_parameters
                        where parameter_english = :englishName and platform = :platform
                        """)
                .param("englishName", englishName)
                .param("platform", platform.value())
                .query(mapper)
                .optional();
    }
}
