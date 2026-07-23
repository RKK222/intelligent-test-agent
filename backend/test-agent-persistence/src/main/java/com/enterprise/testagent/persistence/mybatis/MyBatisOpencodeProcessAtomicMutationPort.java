package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessAssignmentConflictException;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessAtomicMutationPort;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 使用条件更新和受影响行数实现数据库级分配 CAS。 */
@Repository
public class MyBatisOpencodeProcessAtomicMutationPort implements OpencodeProcessAtomicMutationPort {

    private final OpencodeProcessAtomicMutationMapper mapper;

    public MyBatisOpencodeProcessAtomicMutationPort(OpencodeProcessAtomicMutationMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    @Transactional
    public void compareAndSetAssignment(
            OpencodeServerProcess expectedProcess,
            UserOpencodeProcessBinding expectedBinding,
            OpencodeServerProcess replacementProcess,
            UserOpencodeProcessBinding replacementBinding) {
        Objects.requireNonNull(expectedProcess, "expectedProcess must not be null");
        Objects.requireNonNull(expectedBinding, "expectedBinding must not be null");
        Objects.requireNonNull(replacementProcess, "replacementProcess must not be null");
        Objects.requireNonNull(replacementBinding, "replacementBinding must not be null");
        if (mapper.compareAndSetProcessAssignment(expectedProcess, replacementProcess) != 1) {
            throw new OpencodeProcessAssignmentConflictException("TestAgent 进程分配已被并发修改");
        }
        if (mapper.compareAndSetBindingAssignment(expectedBinding, replacementBinding) != 1) {
            // 抛出异常使同一事务中已经成功的 process CAS 一并回滚。
            throw new OpencodeProcessAssignmentConflictException("TestAgent 用户绑定已被并发修改");
        }
    }

    @Override
    public boolean compareAndSetRuntimeState(
            OpencodeServerProcess expectedAssignment,
            OpencodeServerProcess replacementState) {
        Objects.requireNonNull(expectedAssignment, "expectedAssignment must not be null");
        Objects.requireNonNull(replacementState, "replacementState must not be null");
        return mapper.compareAndSetRuntimeState(expectedAssignment, replacementState) == 1;
    }
}
