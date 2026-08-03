package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.modelgateway.ModelGatewayUsageDelta;
import org.apache.ibatis.annotations.Mapper;

/** 模型网关每日聚合 MyBatis mapper。 */
@Mapper
public interface ModelGatewayUsageDailyMapper {

    void increment(ModelGatewayUsageDelta delta);
}
