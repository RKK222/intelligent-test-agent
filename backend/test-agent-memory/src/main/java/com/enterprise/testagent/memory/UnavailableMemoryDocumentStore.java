package com.enterprise.testagent.memory;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.util.List;
import java.util.Optional;

/** 第一阶段安全降级适配器；未启用 memory-service 时既有 QA Run 不受影响。 */
public class UnavailableMemoryDocumentStore implements MemoryDocumentStore {
    @Override public StoredDocument add(AddDocument command) { throw unavailable(); }
    @Override public StoredDocument update(String id, String content, java.util.Map<String, Object> metadata) { throw unavailable(); }
    @Override public Optional<StoredDocument> get(String id) { throw unavailable(); }
    @Override public void delete(String id) { throw unavailable(); }
    @Override public List<StoredDocument> search(SearchQuery query) { throw unavailable(); }
    @Override public List<HistoryEntry> history(String id) { throw unavailable(); }
    @Override public List<ExtractedCandidate> extract(ExtractCommand command) { throw unavailable(); }
    @Override public Health health() { return new Health(false, "DISABLED", null); }

    private PlatformException unavailable() {
        return new PlatformException(ErrorCode.MEMORY_UNAVAILABLE, "长期记忆服务尚未启用");
    }
}
