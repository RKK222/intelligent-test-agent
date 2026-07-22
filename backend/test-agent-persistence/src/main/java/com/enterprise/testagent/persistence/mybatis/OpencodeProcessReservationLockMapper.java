package com.enterprise.testagent.persistence.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户进程端口预留的 PostgreSQL 权威行锁 mapper；SQL 维护在 XML。 */
@Mapper
public interface OpencodeProcessReservationLockMapper {

    String lockUser(@Param("userId") String userId);

    String lockLinuxServer(@Param("linuxServerId") String linuxServerId);
}
