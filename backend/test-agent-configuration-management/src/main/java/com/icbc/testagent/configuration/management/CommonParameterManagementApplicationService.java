package com.icbc.testagent.configuration.management;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.configuration.management.CommonParameterManagementResponses.CommonParameterResponse;
import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterRepository;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 通用参数管理应用服务，编排通用参数的列表查询与「仅修改 value」更新；
 * 不提供新增/删除能力，保持通用参数集合稳定。
 */
@Service
public class CommonParameterManagementApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonParameterManagementApplicationService.class);

    private final CommonParameterRepository repository;
    private final Clock clock;

    /**
     * 注入通用参数领域端口；使用系统默认时钟记录更新时间。
     */
    @Autowired
    public CommonParameterManagementApplicationService(CommonParameterRepository repository) {
        this(repository, Clock.systemUTC());
    }

    /**
     * 测试可注入可控时钟，便于断言更新时间。
     */
    CommonParameterManagementApplicationService(CommonParameterRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 列出通用参数，支持按平台过滤与分页；通用参数表为有界配置表，先全量读取再内存过滤分页。
     */
    public PageResponse<CommonParameterResponse> find(CommonParameterFilter filter, PageRequest pageRequest) {
        Objects.requireNonNull(filter, "filter must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        List<CommonParameter> filtered = repository.findAll().stream()
                .filter(parameter -> filter.platform() == null || parameter.platform() == filter.platform())
                .toList();
        long total = filtered.size();
        int from = (int) Math.min(pageRequest.offset(), total);
        int to = (int) Math.min((long) from + pageRequest.size(), total);
        List<CommonParameterResponse> items = filtered.subList(from, to).stream()
                .map(CommonParameterResponse::from)
                .toList();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), total);
    }

    /**
     * 仅更新指定通用参数的 value；参数不存在抛 {@link ErrorCode#NOT_FOUND}，
     * 新值为空抛 {@link ErrorCode#VALIDATION_ERROR}。
     */
    public CommonParameterResponse updateValue(String parameterId, String newValue, String traceId) {
        String normalizedParameterId = normalize(parameterId, "parameterId");
        CommonParameter existing = repository.findByParameterId(normalizedParameterId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND, "通用参数不存在", Map.of("parameterId", normalizedParameterId)));
        Instant updatedAt = clock.instant();
        CommonParameter updated;
        try {
            updated = existing.withValue(newValue, updatedAt);
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR, "参数值不能为空", Map.of("parameterId", normalizedParameterId), exception);
        }
        int affected = repository.updateValue(normalizedParameterId, updated.parameterValue(), updatedAt);
        if (affected == 0) {
            throw new PlatformException(
                    ErrorCode.NOT_FOUND, "通用参数不存在", Map.of("parameterId", normalizedParameterId));
        }
        CommonParameterResponse response = repository.findByParameterId(normalizedParameterId)
                .map(CommonParameterResponse::from)
                .orElse(CommonParameterResponse.from(updated));
        LOGGER.info("通用参数 value 已更新 traceId={} parameterId={}", traceId, normalizedParameterId);
        return response;
    }

    private static String normalize(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 不能为空", Map.of(field, ""));
        }
        return value.trim();
    }

    /**
     * 通用参数列表过滤条件；platform 为 null 表示不按平台过滤。
     */
    public record CommonParameterFilter(ParameterPlatform platform) {

        /**
         * 解析平台过滤字符串；null/空白返回不过滤，非法值抛 {@link ErrorCode#VALIDATION_ERROR}。
         */
        public static CommonParameterFilter parse(String rawPlatform) {
            if (rawPlatform == null || rawPlatform.isBlank()) {
                return new CommonParameterFilter(null);
            }
            try {
                return new CommonParameterFilter(ParameterPlatform.fromValue(rawPlatform));
            } catch (IllegalArgumentException exception) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR, "平台参数无效", Map.of("platform", rawPlatform), exception);
            }
        }
    }
}
