package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterRepository;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import com.icbc.testagent.persistence.mybatis.CommonParameterMapper;
import com.icbc.testagent.persistence.mybatis.MyBatisCommonParameterRepository;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 验证 CommonParameter 试点仓储通过 MyBatis XML SQL 访问数据库。
 */
class MyBatisCommonParameterRepositoryIntegrationTest {

    private SingleConnectionDataSource dataSource;
    private CommonParameterRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_mybatis_common_parameter_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory();
        CommonParameterMapper mapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(CommonParameterMapper.class);
        repository = new MyBatisCommonParameterRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void commonParametersAreReadThroughMyBatisXmlMapper() {
        // OPENCODE 路径参数已收敛为 all 单行，值引用 ${SYS_DATA_ROOT_DIR}，由解析器在运行态展开。
        assertThat(repository.findByEnglishNameAndPlatform("OPENCODE_APP_WORKSPACE_ROOT", ParameterPlatform.ALL))
                .map(CommonParameter::parameterValue)
                .contains("${SYS_DATA_ROOT_DIR}/agent-opencode/workspace/appworkspace/");
        assertThat(repository.findByEnglishNameAndPlatform("OPENCODE_SESSION_DIR", ParameterPlatform.ALL))
                .map(CommonParameter::parameterValue)
                .contains("${SYS_DATA_ROOT_DIR}/agent-opencode/.session/");
        assertThat(repository.findByEnglishNameAndPlatform("SYS_DATA_ROOT_DIR", ParameterPlatform.MACOS))
                .map(CommonParameter::parameterValue)
                .contains("$HOME/.testagent");
        assertThat(repository.findByEnglishNameAndPlatform("SYS_DATA_ROOT_DIR", ParameterPlatform.LINUX))
                .map(CommonParameter::parameterValue)
                .contains("/data/.testagent");
        assertThat(repository.findByEnglishNameAndPlatform("SYS_DATA_ROOT_DIR", ParameterPlatform.WINDOWS))
                .map(CommonParameter::parameterValue)
                .contains("D:/data/.testagent");
        assertThat(repository.findAll())
                .extracting(CommonParameter::englishName)
                .contains("OPENCODE_PUBLIC_AGENT_GIT_URL", "OPENCODE_PUBLIC_CONFIG_GIT_ROOT");
    }

    @Test
    void commonParameterValueUpdatesUseMyBatisXmlMapper() {
        CommonParameter parameter = repository.findByEnglishNameAndPlatform(
                        "OPENCODE_PUBLIC_AGENT_GIT_URL",
                        ParameterPlatform.ALL)
                .orElseThrow();
        Instant updatedAt = parameter.updatedAt().plusSeconds(60);

        int affected = repository.updateValue(parameter.parameterId(), "https://git.example.com/opencode/agents.git", updatedAt);

        assertThat(affected).isEqualTo(1);
        assertThat(repository.findByParameterId(parameter.parameterId()))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.parameterValue()).isEqualTo("https://git.example.com/opencode/agents.git");
                    assertThat(saved.updatedAt()).isEqualTo(updatedAt);
                    assertThat(saved.createdAt()).isEqualTo(parameter.createdAt());
                });
    }

    /**
     * 直接用 MyBatis-Spring 构造测试仓储，确保 XML mapper 在不启动完整应用时也可加载。
     */
    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
