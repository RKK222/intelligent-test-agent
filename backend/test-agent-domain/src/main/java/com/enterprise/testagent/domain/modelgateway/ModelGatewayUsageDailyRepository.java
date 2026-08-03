package com.enterprise.testagent.domain.modelgateway;

/** 模型网关每日聚合用量写入端口。 */
public interface ModelGatewayUsageDailyRepository {

    /** 使用数据库原子 upsert 累加一次请求。 */
    void increment(ModelGatewayUsageDelta delta);
}
