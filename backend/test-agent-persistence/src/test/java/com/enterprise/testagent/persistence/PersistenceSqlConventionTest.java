package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 固化持久层 SQL 规范：存量 JDBC 允许留在白名单，新关系型 SQL 必须走 MyBatis XML。
 */
class PersistenceSqlConventionTest {

    private static final Set<String> LEGACY_JDBC_FILES = Set.of(
            "JdbcAgentConfigRepository.java",
            "JdbcAgentSessionBindingRepository.java",
            "JdbcAiModelConfigRepository.java",
            "JdbcClientConfig.java",
            "JdbcCommonParameterRepository.java",
            "JdbcConfigurationManagementRepository.java",
            "JdbcDictionaryRepository.java",
            "JdbcExecutionNodeRepository.java",
            "JdbcManagedWorkspaceRepository.java",
            "JdbcOpencodeProcessManagementRepository.java",
            "JdbcRepositorySupport.java",
            "JdbcRoutingDecisionRepository.java",
            "JdbcRunEventRepository.java",
            "JdbcRunRepository.java",
            "JdbcScheduledTaskRepository.java",
            "JdbcSessionMessageRepository.java",
            "JdbcSessionRepository.java",
            "JdbcUserLoginLogRepository.java",
            "JdbcUserRepository.java",
            "JdbcUserRoleRepository.java",
            "JdbcWorkspaceCreateOperationRepository.java",
            "JdbcWorkspaceRepository.java");

    @Test
    void commonParameterRepositoryUsesMyBatisAsTheSpringBean() throws IOException {
        Path javaRoot = locate("src/main/java");
        Path resourceRoot = locate("src/main/resources");

        assertThat(Files.readString(javaRoot.resolve("com/enterprise/testagent/persistence/JdbcCommonParameterRepository.java")))
                .doesNotContain("@Repository");
        assertThat(javaRoot.resolve("com/enterprise/testagent/persistence/mybatis/MyBatisCommonParameterRepository.java"))
                .exists();
        assertThat(javaRoot.resolve("com/enterprise/testagent/persistence/mybatis/CommonParameterMapper.java"))
                .exists();
        assertThat(resourceRoot.resolve("mybatis/CommonParameterMapper.xml"))
                .exists();
    }

    @Test
    void configurationManagementRepositoryUsesMyBatisAsTheSpringBean() throws IOException {
        Path javaRoot = locate("src/main/java");
        Path resourceRoot = locate("src/main/resources");

        assertThat(Files.readString(javaRoot.resolve("com/enterprise/testagent/persistence/JdbcConfigurationManagementRepository.java")))
                .doesNotContain("@Repository");
        assertThat(javaRoot.resolve("com/enterprise/testagent/persistence/mybatis/MyBatisConfigurationManagementRepository.java"))
                .exists();
        assertThat(javaRoot.resolve("com/enterprise/testagent/persistence/mybatis/ConfigurationManagementMapper.java"))
                .exists();
        assertThat(resourceRoot.resolve("mybatis/ConfigurationManagementMapper.xml"))
                .exists();
    }

    @Test
    void runEventRepositoryUsesMyBatisAsTheSpringBean() throws IOException {
        Path javaRoot = locate("src/main/java");
        Path resourceRoot = locate("src/main/resources");

        assertThat(Files.readString(javaRoot.resolve("com/enterprise/testagent/persistence/JdbcRunEventRepository.java")))
                .doesNotContain("@Repository");
        assertThat(javaRoot.resolve("com/enterprise/testagent/persistence/mybatis/MyBatisRunEventRepository.java"))
                .exists();
        assertThat(javaRoot.resolve("com/enterprise/testagent/persistence/mybatis/RunEventMapper.java"))
                .exists();
        assertThat(resourceRoot.resolve("mybatis/RunEventMapper.xml"))
                .exists();
    }

    @Test
    void runRepositoryUsesMyBatisAsTheSpringBean() throws IOException {
        Path javaRoot = locate("src/main/java");
        Path resourceRoot = locate("src/main/resources");

        assertThat(Files.readString(javaRoot.resolve("com/enterprise/testagent/persistence/JdbcRunRepository.java")))
                .doesNotContain("@Repository");
        assertThat(javaRoot.resolve("com/enterprise/testagent/persistence/mybatis/MyBatisRunRepository.java"))
                .exists();
        assertThat(javaRoot.resolve("com/enterprise/testagent/persistence/mybatis/RunMapper.java"))
                .exists();
        assertThat(resourceRoot.resolve("mybatis/RunMapper.xml"))
                .exists();
    }

    @Test
    void runSummaryPersistenceUsesMyBatisXmlAndNeverPersistsPartsJson() throws IOException {
        Path javaRoot = locate("src/main/java");
        Path resourceRoot = locate("src/main/resources");

        assertThat(javaRoot.resolve("com/enterprise/testagent/persistence/mybatis/MyBatisRunSummaryPersistenceRepository.java"))
                .exists();
        assertThat(javaRoot.resolve("com/enterprise/testagent/persistence/mybatis/RunSummaryMapper.java"))
                .exists();
        String mapperXml = Files.readString(resourceRoot.resolve("mybatis/RunSummaryMapper.xml"));
        assertThat(mapperXml)
                .contains("parts_json")
                .contains("cast(null as varchar)")
                .contains("on conflict(session_id, client_request_id) do nothing")
                .doesNotContain("payload_json");
    }

    @Test
    void runSessionScopeMergeUsesTimestampCastsForPostgreSql() throws IOException {
        Path resourceRoot = locate("src/main/resources");
        String mapperXml = Files.readString(resourceRoot.resolve("mybatis/RunSessionScopeMapper.xml"));

        assertThat(mapperXml)
                .as("PostgreSQL MERGE USING (VALUES ...) 会把未定型时间参数推断为 text，时间列必须显式 cast")
                .contains("cast(#{row.createdAt} as timestamp)")
                .contains("cast(#{row.updatedAt} as timestamp)")
                .contains("cast(#{row.discoveredAt} as timestamp)");
    }

    @Test
    void newJdbcSqlIsRestrictedToLegacyAllowlist() throws IOException {
        Path persistenceRoot = locate("src/main/java/com/enterprise/testagent/persistence");
        Set<String> jdbcFiles;
        try (var stream = Files.walk(persistenceRoot)) {
            jdbcFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(PersistenceSqlConventionTest::containsJdbcSqlApi)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }

        assertThat(jdbcFiles)
                .as("新增关系型 SQL 不得继续使用 JdbcClient/RowMapper，应新增 MyBatis XML mapper")
                .isSubsetOf(LEGACY_JDBC_FILES);
    }

    @Test
    void myBatisMappersKeepSqlInXmlFiles() throws IOException {
        Path mapperRoot = locate("src/main/java/com/enterprise/testagent/persistence/mybatis");
        Set<String> annotationSqlUsages;
        try (var stream = Files.walk(mapperRoot)) {
            annotationSqlUsages = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(PersistenceSqlConventionTest::containsMyBatisSqlAnnotation)
                    .map(path -> mapperRoot.relativize(path).toString())
                    .collect(Collectors.toSet());
        }

        assertThat(annotationSqlUsages)
                .as("MyBatis mapper 只能声明方法，SQL 必须写在 XML 中")
                .isEmpty();
    }

    private static boolean containsJdbcSqlApi(Path path) {
        try {
            String content = Files.readString(path);
            return content.contains("org.springframework.jdbc.core.simple.JdbcClient")
                    || content.contains("org.springframework.jdbc.core.RowMapper");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }

    private static boolean containsMyBatisSqlAnnotation(Path path) {
        try {
            String content = Files.readString(path);
            return content.contains("@Select")
                    || content.contains("@Insert")
                    || content.contains("@Update")
                    || content.contains("@Delete");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }

    /**
     * Maven 可能从 backend 根目录或模块目录执行测试，按两种入口定位文件。
     */
    private static Path locate(String relativePath) {
        Path cwd = Path.of("").toAbsolutePath();
        return Set.of(
                        cwd.resolve(relativePath),
                        cwd.resolve("test-agent-persistence").resolve(relativePath),
                        cwd.resolve("backend/test-agent-persistence").resolve(relativePath))
                .stream()
                .filter(Files::exists)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot locate " + relativePath + " from " + cwd));
    }
}
