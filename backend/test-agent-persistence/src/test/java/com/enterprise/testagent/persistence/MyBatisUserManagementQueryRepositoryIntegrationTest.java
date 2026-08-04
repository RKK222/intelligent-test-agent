package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.UserRole;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserManagementQuery;
import com.enterprise.testagent.domain.user.UserManagementQueryRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisUserManagementQueryRepository;
import com.enterprise.testagent.persistence.mybatis.UserManagementQueryMapper;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 验证用户管理页的角色、组织和部门组合筛选，以及未分配角色用户检索。
 */
class MyBatisUserManagementQueryRepositoryIntegrationTest {

    private SingleConnectionDataSource dataSource;
    private UserManagementQueryRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_user_management_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("5").load().migrate();

        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        JdbcUserRepository userRepository = new JdbcUserRepository(jdbcClient);
        JdbcUserRoleRepository userRoleRepository = new JdbcUserRoleRepository(jdbcClient);
        JdbcDictionaryRepository dictionaryRepository = new JdbcDictionaryRepository(jdbcClient);
        User alice = User.createNew(
                "usr_alice", "AUTH_ALICE", "Alice", "hash", "总行科技", "研发中心", "质量保障部");
        User bob = User.createNew(
                "usr_bob", "AUTH_BOB", "Bob", "hash", "总行科技", "研发中心", "平台研发部");
        User roleless = User.createNew(
                "usr_roleless", "AUTH_EMPTY", "Roleless", "hash", "分行", "业务中心", "测试部");
        userRepository.save(alice);
        userRepository.save(bob);
        userRepository.save(roleless);
        userRoleRepository.save(UserRole.create(
                alice.userId(),
                dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, Dictionary.ROLE_APP_ADMIN)
                        .orElseThrow()
                        .dictId()));
        userRoleRepository.save(UserRole.create(
                bob.userId(),
                dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, Dictionary.ROLE_USER)
                        .orElseThrow()
                        .dictId()));

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory();
        UserManagementQueryMapper mapper = new SqlSessionTemplate(sqlSessionFactory)
                .getMapper(UserManagementQueryMapper.class);
        repository = new MyBatisUserManagementQueryRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void combinesKeywordRoleOrganizationAndDepartmentFilters() {
        var page = repository.findPage(
                new UserManagementQuery("AUTH_A", "APP_ADMIN", "总行", "研发", "保障"),
                new PageRequest(1, 20));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(User::username).containsExactly("Alice");
    }

    @Test
    void findsOnlyUsersWithoutAnAssignedGlobalRole() {
        var page = repository.findPage(
                new UserManagementQuery(null, UserManagementQuery.ROLE_UNASSIGNED, null, null, null),
                new PageRequest(1, 20));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(User::userId)
                .extracting(userId -> userId.value())
                .containsExactly("usr_roleless");
    }

    /** 仅加载本次查询 mapper，避免集成测试依赖整个 Spring 应用上下文。 */
    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/UserManagementQueryMapper.xml"));
        return factoryBean.getObject();
    }
}
