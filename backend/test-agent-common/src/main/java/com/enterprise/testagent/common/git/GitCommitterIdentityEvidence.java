package com.enterprise.testagent.common.git;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** 本地远端跟踪引用中已被 SCM 接受的提交者身份。 */
public record GitCommitterIdentityEvidence(
        String name,
        String email,
        Instant committedAt,
        String commitHash) {

    private static final Pattern COMMIT_HASH = Pattern.compile("[0-9a-fA-F]{7,64}");

    public GitCommitterIdentityEvidence {
        name = Objects.requireNonNull(name, "name must not be null").trim();
        email = Objects.requireNonNull(email, "email must not be null").trim();
        Objects.requireNonNull(committedAt, "committedAt must not be null");
        commitHash = Objects.requireNonNull(commitHash, "commitHash must not be null").trim();
        if (name.isEmpty() || name.length() > 128 || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("name is invalid");
        }
        if (email.isEmpty() || email.indexOf('\r') >= 0 || email.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("email is invalid");
        }
        if (!COMMIT_HASH.matcher(commitHash).matches()) {
            throw new IllegalArgumentException("commitHash is invalid");
        }
        commitHash = commitHash.toLowerCase(Locale.ROOT);
    }
}
