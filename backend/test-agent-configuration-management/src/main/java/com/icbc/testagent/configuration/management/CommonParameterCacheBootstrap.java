package com.icbc.testagent.configuration.management;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * 通用参数内存缓存启动加载；在所有单例初始化完成后、Web 服务就绪前全量加载一次，
 * 避免请求到达时读到空缓存。加载失败仅记录错误不阻断启动，后续刷新事件会恢复缓存。
 * 加载成功后同步写出本进程加载快照，使管理端在首次参数修改前即可展示各进程加载值。
 */
@Component
public class CommonParameterCacheBootstrap implements SmartInitializingSingleton {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonParameterCacheBootstrap.class);

    private final CommonParameterCacheRefresher refresher;

    public CommonParameterCacheBootstrap(CommonParameterCacheRefresher refresher) {
        this.refresher = refresher;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            refresher.reloadAndRecordSnapshot("bootstrap");
            LOGGER.info("通用参数内存缓存已启动加载并写出加载快照");
        } catch (RuntimeException exception) {
            LOGGER.error("通用参数内存缓存启动加载失败，降级为空缓存，等待刷新事件恢复", exception);
        }
    }
}
