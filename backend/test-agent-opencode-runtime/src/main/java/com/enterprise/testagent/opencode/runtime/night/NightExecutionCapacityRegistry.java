package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.domain.configuration.CommonParameterMemoryEntry;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryKey;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryLoadException;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryLoadedValue;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 夜间任务时段容量的 JVM 内存注册表。
 *
 * <p>数据库通用参数是唯一权威来源；通用内存参数注册表负责启动和运行期调用本条目，
 * 本条目先完整读取并校验新值，再原子替换不可变快照，避免请求读到中间状态。
 */
@Service
public class NightExecutionCapacityRegistry implements CommonParameterMemoryEntry {

    public static final String PARAMETER_ENGLISH_NAME = "NIGHT_EXECUTION_SLOT_CAPACITY";
    private static final CommonParameterMemoryKey KEY =
            new CommonParameterMemoryKey(PARAMETER_ENGLISH_NAME, ParameterPlatform.ALL);

    private final CommonParameterValues commonParameterValues;
    private volatile Snapshot snapshot = Snapshot.unloaded();

    public NightExecutionCapacityRegistry(CommonParameterValues commonParameterValues) {
        this.commonParameterValues = Objects.requireNonNull(
                commonParameterValues, "commonParameterValues must not be null");
    }

    /** 返回该内存参数唯一键。 */
    @Override
    public CommonParameterMemoryKey key() {
        return KEY;
    }

    /**
     * 从数据库读取正整数容量并原子替换业务快照；任何失败都发生在替换之前。
     */
    @Override
    public CommonParameterMemoryLoadedValue reloadFromDatabase() {
        String sourceValue;
        try {
            sourceValue = commonParameterValues
                    .resolvedValue(PARAMETER_ENGLISH_NAME, ParameterPlatform.ALL)
                    .orElseThrow(() -> new CommonParameterMemoryLoadException("数据库值缺失"));
        } catch (CommonParameterMemoryLoadException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CommonParameterMemoryLoadException("数据库读取失败");
        }
        int capacity = positiveCapacity(sourceValue);
        CommonParameterMemoryLoadedValue loadedValue =
                new CommonParameterMemoryLoadedValue(sourceValue, Integer.toString(capacity));
        snapshot = new Snapshot(capacity);
        return loadedValue;
    }

    /** 返回当前已加载的正整数容量；启动监听尚未完成时拒绝提供未初始化值。 */
    public int currentCapacity() {
        Snapshot current = snapshot;
        if (!current.loaded()) {
            throw new IllegalStateException("通用参数 NIGHT_EXECUTION_SLOT_CAPACITY 尚未加载");
        }
        return current.capacity();
    }

    private static int positiveCapacity(String raw) {
        try {
            int capacity = Integer.parseInt(raw.trim());
            if (capacity <= 0) {
                throw new NumberFormatException("capacity must be positive");
            }
            return capacity;
        } catch (RuntimeException exception) {
            throw new CommonParameterMemoryLoadException("数据库值不是正整数");
        }
    }

    private record Snapshot(int capacity) {
        private static Snapshot unloaded() {
            return new Snapshot(0);
        }

        private boolean loaded() {
            return capacity > 0;
        }
    }
}
