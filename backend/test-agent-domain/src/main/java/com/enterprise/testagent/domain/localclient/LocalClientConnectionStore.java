package com.enterprise.testagent.domain.localclient;

import java.time.Duration;
import java.util.Optional;

/** 本地客户端实时连接路由存储端口；实现必须用 generation 做原子 fencing。 */
public interface LocalClientConnectionStore {

    long nextGeneration();

    void save(LocalClientConnectionRoute route, Duration ttl);

    Optional<LocalClientConnectionRoute> find(LocalClientInstanceId clientInstanceId);

    boolean refresh(LocalClientConnectionRoute route, Duration ttl);

    boolean delete(LocalClientInstanceId clientInstanceId, long connectionGeneration);
}
