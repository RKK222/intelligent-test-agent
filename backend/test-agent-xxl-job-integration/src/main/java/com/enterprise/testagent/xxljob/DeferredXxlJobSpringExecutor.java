package com.enterprise.testagent.xxljob;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;

/**
 * 延迟 XXL 上游 executor 的初始化回调，直到至少一个 Admin readiness 已返回 HTTP 200。
 * 该扩展只覆盖生命周期入口，不修改 XXL-JOB 上游源码，便于后续整体升级上游模块。
 */
class DeferredXxlJobSpringExecutor extends XxlJobSpringExecutor {

    private boolean startAttempted;
    private boolean started;

    /** Spring 单例初始化阶段只完成 Bean 装配，不提前创建 9999 端口和注册线程。 */
    @Override
    public void afterSingletonsInstantiated() {
        // 由 XxlJobExecutorLifecycle 在 Admin readiness 就绪后显式启动。
    }

    /** readiness 协调器的唯一启动入口；重复调用不会重复创建上游线程或监听端口。 */
    synchronized void startAfterAdminReady() {
        if (started) {
            return;
        }
        if (startAttempted) {
            throw new IllegalStateException("XXL-JOB executor initialization has already failed");
        }
        startAttempted = true;
        try {
            super.afterSingletonsInstantiated();
            started = true;
        } catch (RuntimeException exception) {
            cleanupFailedStart();
            throw exception;
        }
    }

    synchronized boolean isStarted() {
        return started;
    }

    /** Spring 销毁回调和生命周期 stop 可能同时到达，必须保证上游 destroy 最多执行一次。 */
    @Override
    public synchronized void destroy() {
        if (!started) {
            return;
        }
        started = false;
        super.destroy();
    }

    private void cleanupFailedStart() {
        try {
            // 上游在端口监听失败前可能已经创建清理/回调线程，尽力释放已初始化资源。
            super.destroy();
        } catch (RuntimeException ignored) {
            // 上游 destroy 对部分初始化状态并非完全空安全；原始启动异常才是稳定诊断依据。
        }
    }
}
