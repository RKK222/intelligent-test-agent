package com.icbc.testagent.persistence;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.user.User;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserRepository;
import com.icbc.testagent.domain.user.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 用户 JDBC Repository，负责领域对象和 users 表之间的字段映射。
 */
@Repository
public class JdbcUserRepository extends JdbcRepositorySupport implements UserRepository {

    private final JdbcClient jdbcClient;

    private final RowMapper<User> rowMapper = (rs, rowNum) -> new User(
            new UserId(rs.getString("user_id")),
            rs.getString("unified_auth_id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("organization"),
            rs.getString("rd_department"),
            rs.getString("department"),
            UserStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    /**
     * 注入 JdbcClient。
     */
    public JdbcUserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 保存用户；存在时全量更新，缺失时插入新记录。
     */
    @Override
    public void save(User user) {
        if (findByUserId(user.userId()).isPresent()) {
            jdbcClient.sql("""
                            update users
                            set unified_auth_id = :unifiedAuthId, username = :username,
                                password_hash = :passwordHash, organization = :organization,
                                rd_department = :rdDepartment, department = :department,
                                status = :status, updated_at = :updatedAt
                            where user_id = :userId
                            """)
                    .param("userId", user.userId().value())
                    .param("unifiedAuthId", user.unifiedAuthId())
                    .param("username", user.username())
                    .param("passwordHash", user.passwordHash())
                    .param("organization", user.organization())
                    .param("rdDepartment", user.rdDepartment())
                    .param("department", user.department())
                    .param("status", user.status().name())
                    .param("updatedAt", timestamp(user.updatedAt()))
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into users(user_id, unified_auth_id, username, password_hash,
                                organization, rd_department, department, status, created_at, updated_at)
                            values (:userId, :unifiedAuthId, :username, :passwordHash,
                                :organization, :rdDepartment, :department, :status, :createdAt, :updatedAt)
                            """)
                    .param("userId", user.userId().value())
                    .param("unifiedAuthId", user.unifiedAuthId())
                    .param("username", user.username())
                    .param("passwordHash", user.passwordHash())
                    .param("organization", user.organization())
                    .param("rdDepartment", user.rdDepartment())
                    .param("department", user.department())
                    .param("status", user.status().name())
                    .param("createdAt", timestamp(user.createdAt()))
                    .param("updatedAt", timestamp(user.updatedAt()))
                    .update();
        }
    }

    /**
     * 按用户业务 ID 查询用户。
     */
    @Override
    public Optional<User> findByUserId(UserId userId) {
        return jdbcClient.sql("""
                        select user_id, unified_auth_id, username, password_hash,
                            organization, rd_department, department, status, created_at, updated_at
                        from users
                        where user_id = :userId
                        """)
                .param("userId", userId.value())
                .query(rowMapper)
                .optional();
    }

    /**
     * 按统一认证号查找用户。
     */
    @Override
    public Optional<User> findByUnifiedAuthId(String unifiedAuthId) {
        return jdbcClient.sql("""
                        select user_id, unified_auth_id, username, password_hash,
                            organization, rd_department, department, status, created_at, updated_at
                        from users
                        where unified_auth_id = :unifiedAuthId
                        """)
                .param("unifiedAuthId", unifiedAuthId)
                .query(rowMapper)
                .optional();
    }

    /**
     * 按用户名查找用户。
     */
    @Override
    public Optional<User> findByUsername(String username) {
        return jdbcClient.sql("""
                        select user_id, unified_auth_id, username, password_hash,
                            organization, rd_department, department, status, created_at, updated_at
                        from users
                        where username = :username
                        """)
                .param("username", username)
                .query(rowMapper)
                .optional();
    }

    /**
     * 分页搜索用户；keyword 为空时返回全部用户，非空时匹配用户名或统一认证号。
     */
    @Override
    public PageResponse<User> findPage(String keyword, PageRequest pageRequest) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase();
        if (normalized.isBlank()) {
            List<User> items = jdbcClient.sql("""
                            select user_id, unified_auth_id, username, password_hash,
                                organization, rd_department, department, status, created_at, updated_at
                            from users
                            order by username, user_id
                            limit :limit offset :offset
                            """)
                    .param("limit", pageRequest.size())
                    .param("offset", pageRequest.offset())
                    .query(rowMapper)
                    .list();
            long total = jdbcClient.sql("select count(*) from users").query(Long.class).single();
            return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), total);
        }
        String pattern = "%" + normalized + "%";
        List<User> items = jdbcClient.sql("""
                        select user_id, unified_auth_id, username, password_hash,
                            organization, rd_department, department, status, created_at, updated_at
                        from users
                        where lower(username) like :pattern or lower(unified_auth_id) like :pattern
                        order by username, user_id
                        limit :limit offset :offset
                        """)
                .param("pattern", pattern)
                .param("limit", pageRequest.size())
                .param("offset", pageRequest.offset())
                .query(rowMapper)
                .list();
        long total = jdbcClient.sql("""
                        select count(*) from users
                        where lower(username) like :pattern or lower(unified_auth_id) like :pattern
                        """)
                .param("pattern", pattern)
                .query(Long.class)
                .single();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), total);
    }

    /**
     * 检查用户名是否已存在。
     */
    @Override
    public boolean existsByUsername(String username) {
        return jdbcClient.sql("select count(*) from users where username = :username")
                .param("username", username)
                .query(Long.class)
                .single() > 0;
    }

    /**
     * 检查统一认证号是否已存在。
     */
    @Override
    public boolean existsByUnifiedAuthId(String unifiedAuthId) {
        return jdbcClient.sql("select count(*) from users where unified_auth_id = :unifiedAuthId")
                .param("unifiedAuthId", unifiedAuthId)
                .query(Long.class)
                .single() > 0;
    }
}
