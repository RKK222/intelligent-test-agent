package com.enterprise.testagent.xxljob.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 平台 XXL 用户扩展 SQL；所有关系型查询均在对应 MyBatis XML 中。 */
@Mapper
public interface PlatformXxlJobUserMapper {

    PlatformXxlJobUser findByPlatformUserId(@Param("platformUserId") String platformUserId);

    PlatformXxlJobUser findByUsername(@Param("username") String username);

    PlatformXxlJobUser findById(@Param("id") int id);

    int upsert(PlatformXxlJobUserUpsert command);

    int updateToken(@Param("id") int id, @Param("token") String token);
}
