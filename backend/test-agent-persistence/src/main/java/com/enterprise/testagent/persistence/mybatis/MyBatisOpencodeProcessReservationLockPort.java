package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessReservationLockPort;
import com.enterprise.testagent.domain.user.UserId;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/** 通过 MyBatis SELECT FOR UPDATE 实现用户优先、服务器其次的事务行锁。 */
@Repository
public class MyBatisOpencodeProcessReservationLockPort implements OpencodeProcessReservationLockPort {

    private final OpencodeProcessReservationLockMapper mapper;

    public MyBatisOpencodeProcessReservationLockPort(OpencodeProcessReservationLockMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public boolean lockUser(UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return mapper.lockUser(userId.value()) != null;
    }

    @Override
    public boolean lockLinuxServer(LinuxServerId linuxServerId) {
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        return mapper.lockLinuxServer(linuxServerId.value()) != null;
    }
}
