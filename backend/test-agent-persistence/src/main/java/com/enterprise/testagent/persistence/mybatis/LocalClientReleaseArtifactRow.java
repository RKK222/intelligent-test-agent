package com.enterprise.testagent.persistence.mybatis;

/** local_client_release_artifacts 的 MyBatis 行模型。 */
public record LocalClientReleaseArtifactRow(
        String version,
        String artifactKind,
        String artifactUrl,
        long artifactSize,
        String artifactSha256,
        String artifactSignature) {
}
