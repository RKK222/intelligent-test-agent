package com.example.testagent.persistence;

import com.example.testagent.domain.dictionary.DictId;
import com.example.testagent.domain.dictionary.UserRole;
import com.example.testagent.domain.dictionary.UserRoleRepository;
import com.example.testagent.domain.user.UserId;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 用户角色 JDBC Repository。
 */
@Repository
public class JdbcUserRoleRepository extends JdbcRepositorySupport implements UserRoleRepository {

    private final JdbcClient jdbcClient;

    private final RowMapper<UserRole> rowMapper = (rs, rowNum) -> new UserRole(
            new UserId(rs.getString("user_id")),
            new DictId(rs.getString("dict_id")),
            instant(rs, "created_at"));

    /**
     * 注入 JdbcClient。
     */
    public JdbcUserRoleRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void save(UserRole userRole) {
        jdbcClient.sql("""
                        insert into user_roles(user_id, dict_id, created_at)
                        values (:userId, :dictId, :createdAt)
                        """)
                .param("userId", userRole.userId().value())
                .param("dictId", userRole.dictId().value())
                .param("createdAt", timestamp(userRole.createdAt()))
                .update();
    }

    @Override
    public List<UserRole> findByUserId(UserId userId) {
        return jdbcClient.sql("""
                        select user_id, dict_id, created_at
                        from user_roles
                        where user_id = :userId
                        """)
                .param("userId", userId.value())
                .query(rowMapper)
                .list();
    }

    @Override
    public void delete(UserRole userRole) {
        jdbcClient.sql("""
                        delete from user_roles
                        where user_id = :userId and dict_id = :dictId
                        """)
                .param("userId", userRole.userId().value())
                .param("dictId", userRole.dictId().value())
                .update();
    }
}
