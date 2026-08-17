package com.enterprise.testagent.common.git;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 企业 SCM 右控明确指出提交者姓名不匹配时的内部异常；姓名只保留在内存字段中，不进入错误详情。 */
public final class ScmGitIdentityRejectedException extends PlatformException {

    private static final Pattern IDENTITY_MISMATCH = Pattern.compile(
            "客户端提交者邮箱\\s*([A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@mails\\.icbc)\\s*"
                    + "对应的姓名应为\\s*([^,，\\r\\n]{1,128}?)\\s*[,，]\\s*"
                    + "您的提交者姓名为\\s*([^,，\\r\\n]{1,128}?)\\s*[,，]\\s*校验不一致");
    private static final Pattern COMMIT = Pattern.compile("commit:?\\s*([0-9a-fA-F]{7,64})提交失败");

    private final String expectedName;
    private final String actualName;
    private final String email;
    private final String evidenceCommit;

    private ScmGitIdentityRejectedException(
            String expectedName,
            String actualName,
            String email,
            String evidenceCommit,
            Map<String, Object> safeDetails) {
        super(ErrorCode.GIT_UNAVAILABLE, "SCM 拒绝了提交者姓名，请使用 SCM 登记姓名重新提交", safeDetails);
        this.expectedName = expectedName;
        this.actualName = actualName;
        this.email = email;
        this.evidenceCommit = evidenceCommit;
    }

    /** 仅接受右控固定句式，避免把普通远端报错误判为可自动修改提交的身份拒绝。 */
    public static Optional<ScmGitIdentityRejectedException> parse(
            String rawStderr,
            String safeCommand,
            int exitCode) {
        if (rawStderr == null || rawStderr.length() > 64 * 1024) {
            return Optional.empty();
        }
        Matcher matcher = IDENTITY_MISMATCH.matcher(rawStderr);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String expectedName = matcher.group(2).trim();
        String actualName = matcher.group(3).trim();
        if (expectedName.isEmpty() || actualName.isEmpty()) {
            return Optional.empty();
        }
        Matcher commitMatcher = COMMIT.matcher(rawStderr);
        String commit = commitMatcher.find() ? commitMatcher.group(1).toLowerCase() : null;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("command", safeCommand);
        details.put("exitCode", exitCode);
        details.put("gitFailureType", "REMOTE_REJECTED");
        details.put("gitFailureReason", "SCM_IDENTITY_MISMATCH");
        details.put("gitFailureHint", "平台将按 SCM 右控返回的登记姓名重建提交并仅重试一次。");
        return Optional.of(new ScmGitIdentityRejectedException(
                expectedName, actualName, matcher.group(1), commit, details));
    }

    public String expectedName() {
        return expectedName;
    }

    public String actualName() {
        return actualName;
    }

    public String email() {
        return email;
    }

    public String evidenceCommit() {
        return evidenceCommit;
    }
}
