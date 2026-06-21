package com.example.testagent.domain.dictionary;

import java.util.List;
import java.util.Optional;

/**
 * 字典仓储接口。
 */
public interface DictionaryRepository {

    /**
     * 保存字典。
     */
    void save(Dictionary dictionary);

    /**
     * 根据字典 ID 查找字典。
     */
    Optional<Dictionary> findByDictId(DictId dictId);

    /**
     * 根据字典 Key 查找字典列表。
     */
    List<Dictionary> findByDictKey(String dictKey);

    /**
     * 根据字典 Key 和 Value 查找字典。
     */
    Optional<Dictionary> findByDictKeyAndValue(String dictKey, String dictValue);

    /**
     * 删除字典。
     */
    void delete(DictId dictId);
}
