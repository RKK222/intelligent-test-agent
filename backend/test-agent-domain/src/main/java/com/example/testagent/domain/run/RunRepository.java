package com.example.testagent.domain.run;

import java.util.Optional;

/**
 * Run 持久化端口，状态迁移仍由 Run 聚合自身负责。
 */
public interface RunRepository {

    Run save(Run run);

    Optional<Run> findById(RunId runId);
}
