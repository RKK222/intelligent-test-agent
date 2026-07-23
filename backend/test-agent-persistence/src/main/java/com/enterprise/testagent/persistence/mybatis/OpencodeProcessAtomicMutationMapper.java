package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 进程分配 CAS 与运行态字段更新 mapper；SQL 统一维护在 XML。 */
@Mapper
public interface OpencodeProcessAtomicMutationMapper {

    int compareAndSetProcessAssignment(
            @Param("expected") OpencodeServerProcess expected,
            @Param("replacement") OpencodeServerProcess replacement);

    int compareAndSetBindingAssignment(
            @Param("expected") UserOpencodeProcessBinding expected,
            @Param("replacement") UserOpencodeProcessBinding replacement);

    int compareAndSetRuntimeState(
            @Param("expected") OpencodeServerProcess expected,
            @Param("replacement") OpencodeServerProcess replacement);
}
