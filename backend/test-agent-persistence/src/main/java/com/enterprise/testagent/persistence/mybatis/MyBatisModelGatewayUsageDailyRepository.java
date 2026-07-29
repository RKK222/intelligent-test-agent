package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.modelgateway.ModelGatewayUsageDailyRepository;
import com.enterprise.testagent.domain.modelgateway.ModelGatewayUsageDelta;
import org.springframework.stereotype.Repository;

/** 使用单条 MyBatis XML upsert 原子累加每日网关用量。 */
@Repository
public class MyBatisModelGatewayUsageDailyRepository implements ModelGatewayUsageDailyRepository {

    private final ModelGatewayUsageDailyMapper mapper;

    public MyBatisModelGatewayUsageDailyRepository(ModelGatewayUsageDailyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void increment(ModelGatewayUsageDelta delta) {
        mapper.increment(delta);
    }
}
