package com.enterprise.testagent.opencode.runtime.observability;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeObservabilityGenerationRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartupRequest;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 为单个服务端 OpenCode 进程签发专用 Observability 凭据。
 * 凭据绑定用户、进程、服务器、容器、端口和启动 generation，不能调用平台其它 API。
 */
@Service
public class OpencodeObservabilityTokenService {

    public static final String ENDPOINT_PATH = "/api/internal/agent/opencode-observability/v1/events";
    public static final String GENERATION_HEADER_NAME = "X-Test-Agent-Observability-Generation";
    public static final String BASE_URL_ENV_NAME = "TEST_AGENT_OBSERVABILITY_BASE_URL";
    public static final String TOKEN_ENV_NAME = "TEST_AGENT_OBSERVABILITY_TOKEN";
    public static final String RUNTIME_KIND_ENV_NAME = "TEST_AGENT_OBSERVABILITY_RUNTIME_KIND";
    public static final String GENERATION_ENV_NAME = "TEST_AGENT_OBSERVABILITY_GENERATION";
    public static final String PROCESS_ID_ENV_NAME = "TEST_AGENT_OBSERVABILITY_PROCESS_ID";
    public static final String SERVER_ID_ENV_NAME = "TEST_AGENT_OBSERVABILITY_SERVER_ID";
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofDays(7);
    private static final Duration MIN_TOKEN_TTL = Duration.ofMinutes(1);
    private static final Duration MAX_TOKEN_TTL = Duration.ofDays(30);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ManagerControlSettings managerSettings;
    private final OpencodeProcessManagementRepository processRepository;
    private final OpencodeObservabilityGenerationRepository generationRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final Duration tokenTtl;

    @Autowired
    public OpencodeObservabilityTokenService(
            ManagerControlSettings managerSettings,
            OpencodeProcessManagementRepository processRepository,
            OpencodeObservabilityGenerationRepository generationRepository,
            UserRepository userRepository,
            @Value("${test-agent.observability.token-ttl:7d}") Duration tokenTtl) {
        this(managerSettings, processRepository, generationRepository, userRepository, Clock.systemUTC(), tokenTtl);
    }

    OpencodeObservabilityTokenService(
            ManagerControlSettings managerSettings,
            OpencodeProcessManagementRepository processRepository,
            OpencodeObservabilityGenerationRepository generationRepository,
            UserRepository userRepository,
            Clock clock) {
        this(managerSettings, processRepository, generationRepository, userRepository, clock, DEFAULT_TOKEN_TTL);
    }

    OpencodeObservabilityTokenService(
            ManagerControlSettings managerSettings,
            OpencodeProcessManagementRepository processRepository,
            OpencodeObservabilityGenerationRepository generationRepository,
            UserRepository userRepository,
            Clock clock,
            Duration tokenTtl) {
        this.managerSettings = managerSettings;
        this.processRepository = processRepository;
        this.generationRepository = generationRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        if (tokenTtl == null || tokenTtl.compareTo(MIN_TOKEN_TTL) < 0 || tokenTtl.compareTo(MAX_TOKEN_TTL) > 0) {
            throw new IllegalArgumentException("Observability token TTL must be between 1 minute and 30 days");
        }
        this.tokenTtl = tokenTtl;
    }

    public String issue(OpencodeProcessStartupRequest request) {
        if (request.processId() == null) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Observability 凭据缺少进程身份");
        }
        long expiresAt = clock.instant().plus(tokenTtl).getEpochSecond();
        String payload = String.join("\n",
                request.userId().value(),
                request.processId().value(),
                request.linuxServerId().value(),
                request.containerId().value(),
                Integer.toString(request.port()),
                request.traceId(),
                Long.toString(expiresAt));
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    /** 校验专用 Bearer、请求 generation 和当前数据库进程代次，拒绝旧进程继续上传。 */
    public Principal authenticate(String authorization, String generation) {
        String token = bearerToken(authorization);
        String[] tokenParts = token.split("\\.", -1);
        if (tokenParts.length != 2
                || !MessageDigest.isEqual(
                        sign(tokenParts[0]).getBytes(StandardCharsets.US_ASCII),
                        tokenParts[1].getBytes(StandardCharsets.US_ASCII))) {
            throw unauthenticated();
        }
        String[] values = decode(tokenParts[0]);
        long expiresAt;
        int port;
        try {
            port = Integer.parseInt(values[4]);
            expiresAt = Long.parseLong(values[6]);
        } catch (NumberFormatException exception) {
            throw unauthenticated();
        }
        if (!clock.instant().isBefore(Instant.ofEpochSecond(expiresAt))
                || generation == null
                || !MessageDigest.isEqual(
                        values[5].getBytes(StandardCharsets.UTF_8),
                        generation.getBytes(StandardCharsets.UTF_8))) {
            throw unauthenticated();
        }
        UserId userId = new UserId(values[0]);
        OpencodeProcessId processId = new OpencodeProcessId(values[1]);
        LinuxServerId linuxServerId = new LinuxServerId(values[2]);
        OpencodeContainerId containerId = new OpencodeContainerId(values[3]);
        User user = userRepository.findByUserId(userId).filter(User::canLogin).orElseThrow(this::unauthenticated);
        OpencodeServerProcess process = processRepository.findOpencodeServerProcessById(processId)
                .filter(current -> current.userId().equals(userId))
                .filter(current -> current.linuxServerId().equals(linuxServerId))
                .filter(current -> current.containerId().equals(containerId))
                .filter(current -> current.port() == port)
                .filter(current -> current.status() == OpencodeServerProcessStatus.STARTING
                        || current.status() == OpencodeServerProcessStatus.RUNNING)
                .orElseThrow(this::unauthenticated);
        if (generationRepository.findByProcessId(processId)
                .filter(values[5]::equals)
                .isEmpty()) {
            throw unauthenticated();
        }
        return new Principal(user, process, values[5]);
    }

    private String[] decode(String value) {
        try {
            String[] values = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
                    .split("\\n", -1);
            if (values.length != 7 || java.util.Arrays.stream(values).anyMatch(String::isBlank)) {
                throw unauthenticated();
            }
            return values;
        } catch (IllegalArgumentException exception) {
            throw unauthenticated();
        }
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw unauthenticated();
        }
        String value = authorization.substring("Bearer ".length()).trim();
        if (value.isBlank()) {
            throw unauthenticated();
        }
        return value;
    }

    private String sign(String payload) {
        try {
            String key = managerSettings == null ? null : managerSettings.token();
            if (key == null || key.isBlank()) {
                throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "Observability 尚未配置");
            }
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Observability 凭据签发失败");
        }
    }

    private PlatformException unauthenticated() {
        return new PlatformException(ErrorCode.UNAUTHENTICATED, "Observability 凭据无效或已过期");
    }

    public record Principal(User user, OpencodeServerProcess process, String generation) {
    }
}
