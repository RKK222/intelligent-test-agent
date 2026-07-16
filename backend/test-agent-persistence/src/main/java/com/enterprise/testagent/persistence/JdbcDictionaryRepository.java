package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.dictionary.DictId;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 字典 JDBC Repository。
 */
@Repository
public class JdbcDictionaryRepository extends JdbcRepositorySupport implements DictionaryRepository {

    private final JdbcClient jdbcClient;

    private final RowMapper<Dictionary> rowMapper = (rs, rowNum) -> new Dictionary(
            new DictId(rs.getString("dict_id")),
            rs.getString("dict_name"),
            rs.getString("dict_key"),
            rs.getString("dict_value"),
            rs.getString("dict_label"),
            rs.getInt("sort_order"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    /**
     * 注入 JdbcClient。
     */
    public JdbcDictionaryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void save(Dictionary dictionary) {
        if (findByDictId(dictionary.dictId()).isPresent()) {
            jdbcClient.sql("""
                            update dictionaries
                            set dict_name = :dictName, dict_key = :dictKey, dict_value = :dictValue,
                                dict_label = :dictLabel, sort_order = :sortOrder, updated_at = :updatedAt
                            where dict_id = :dictId
                            """)
                    .param("dictId", dictionary.dictId().value())
                    .param("dictName", dictionary.dictName())
                    .param("dictKey", dictionary.dictKey())
                    .param("dictValue", dictionary.dictValue())
                    .param("dictLabel", dictionary.dictLabel())
                    .param("sortOrder", dictionary.sortOrder())
                    .param("updatedAt", timestamp(dictionary.updatedAt()))
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into dictionaries(dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at)
                            values (:dictId, :dictName, :dictKey, :dictValue, :dictLabel, :sortOrder, :createdAt, :updatedAt)
                            """)
                    .param("dictId", dictionary.dictId().value())
                    .param("dictName", dictionary.dictName())
                    .param("dictKey", dictionary.dictKey())
                    .param("dictValue", dictionary.dictValue())
                    .param("dictLabel", dictionary.dictLabel())
                    .param("sortOrder", dictionary.sortOrder())
                    .param("createdAt", timestamp(dictionary.createdAt()))
                    .param("updatedAt", timestamp(dictionary.updatedAt()))
                    .update();
        }
    }

    @Override
    public Optional<Dictionary> findByDictId(DictId dictId) {
        return jdbcClient.sql("""
                        select dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at
                        from dictionaries
                        where dict_id = :dictId
                        """)
                .param("dictId", dictId.value())
                .query(rowMapper)
                .optional();
    }

    @Override
    public List<Dictionary> findByDictKey(String dictKey) {
        return jdbcClient.sql("""
                        select dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at
                        from dictionaries
                        where dict_key = :dictKey
                        order by sort_order
                        """)
                .param("dictKey", dictKey)
                .query(rowMapper)
                .list();
    }

    @Override
    public Optional<Dictionary> findByDictKeyAndValue(String dictKey, String dictValue) {
        return jdbcClient.sql("""
                        select dict_id, dict_name, dict_key, dict_value, dict_label, sort_order, created_at, updated_at
                        from dictionaries
                        where dict_key = :dictKey and dict_value = :dictValue
                        """)
                .param("dictKey", dictKey)
                .param("dictValue", dictValue)
                .query(rowMapper)
                .optional();
    }

    @Override
    public void delete(DictId dictId) {
        jdbcClient.sql("delete from dictionaries where dict_id = :dictId")
                .param("dictId", dictId.value())
                .update();
    }
}
