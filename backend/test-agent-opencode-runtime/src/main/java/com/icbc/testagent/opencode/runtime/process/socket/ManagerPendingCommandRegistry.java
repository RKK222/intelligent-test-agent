package com.icbc.testagent.opencode.runtime.process.socket;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * 管理进程命令等待表，用 commandId 关联后端请求与 WebSocket 异步响应。
 */
@Component
public class ManagerPendingCommandRegistry {

    private final ConcurrentMap<String, CompletableFuture<ManagerControlMessage>> pending = new ConcurrentHashMap<>();

    /**
     * 创建等待句柄，重复 commandId 会被拒绝以保持幂等边界清晰。
     */
    public CompletableFuture<ManagerControlMessage> create(String commandId) {
        CompletableFuture<ManagerControlMessage> future = new CompletableFuture<>();
        CompletableFuture<ManagerControlMessage> previous = pending.putIfAbsent(commandId, future);
        if (previous != null) {
            throw new PlatformException(ErrorCode.CONFLICT, "管理进程命令 ID 冲突");
        }
        return future;
    }

    /**
     * 完成指定命令，未知 commandId 会被忽略，便于处理迟到响应。
     */
    public void complete(String commandId, ManagerControlMessage result) {
        CompletableFuture<ManagerControlMessage> future = pending.remove(commandId);
        if (future != null) {
            future.complete(result);
        }
    }

    /**
     * 等待命令结果；超时会删除 pending 项并返回统一 opencode timeout。
     */
    public ManagerControlMessage await(String commandId, Duration timeout) {
        CompletableFuture<ManagerControlMessage> future = pending.get(commandId);
        if (future == null) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "管理进程命令未注册");
        }
        return await(commandId, future, timeout);
    }

    /**
     * 等待 create 返回的句柄；即使 manager 极快回包并提前从 map 删除，也不会丢失结果。
     */
    public ManagerControlMessage await(String commandId, CompletableFuture<ManagerControlMessage> future, Duration timeout) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            pending.remove(commandId, future);
            throw new PlatformException(ErrorCode.OPENCODE_TIMEOUT, "opencode 管理进程命令超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            pending.remove(commandId, future);
            throw new PlatformException(ErrorCode.OPENCODE_TIMEOUT, "opencode 管理进程命令中断");
        } catch (Exception exception) {
            pending.remove(commandId, future);
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "opencode 管理进程响应异常");
        } finally {
            pending.remove(commandId, future);
        }
    }

    /**
     * 命令发送失败时移除等待项，避免泄漏内存。
     */
    public void cancel(String commandId) {
        pending.remove(commandId);
    }
}
