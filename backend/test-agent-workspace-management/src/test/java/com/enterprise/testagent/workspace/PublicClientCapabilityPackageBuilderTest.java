package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityModels;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicClientCapabilityPackageBuilderTest {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path temporary;

    @Test
    void buildsDeterministicWhitelistPackageAndDetectsUnchangedHotReload() throws Exception {
        Fixture fixture = fixture();
        var builder = new PublicClientCapabilityPackageBuilder(objectMapper);
        Instant createdAt = Instant.parse("2026-08-23T00:00:00Z");

        var first = builder.build(
                fixture.config(), fixture.lock(), fixture.nodeModules(), COMMIT, null, createdAt);
        var firstRelease = release(first, createdAt);
        var second = builder.build(
                fixture.config(), fixture.lock(), fixture.nodeModules(), COMMIT, firstRelease, createdAt);

        assertThat(first.artifact()).isEqualTo(builder.build(
                fixture.config(), fixture.lock(), fixture.nodeModules(), COMMIT, null, createdAt).artifact());
        assertThat(first.bundleDigest()).isEqualTo(second.bundleDigest());
        assertThat(second.requiresRestart()).isFalse();
        assertThat(first.manifestJson()).contains("agents/public.md", "skills/demo/SKILL.md", "tools/demo.ts")
                .contains("runtimeLayoutVersion", "package.json", "package-lock.json", ".gitignore")
                .doesNotContain("AGENTS.md", "opencode.jsonc", "node_modules/.cache", "node_modules/.bin");
        assertThat(first.counts()).isEqualTo(new LocalClientPublicCapabilityModels.Counts(1, 1, 1));
    }

    @Test
    void sameCapabilityFilesAtNewPublicCommitProduceTraceableVersionWithoutRestart() throws Exception {
        Fixture fixture = fixture();
        var builder = new PublicClientCapabilityPackageBuilder(objectMapper);
        Instant createdAt = Instant.parse("2026-08-23T00:00:00Z");
        var first = builder.build(
                fixture.config(), fixture.lock(), fixture.nodeModules(), COMMIT, null, createdAt);
        String nextCommit = "f".repeat(40);

        var next = builder.build(
                fixture.config(), fixture.lock(), fixture.nodeModules(), nextCommit, release(first, createdAt), createdAt);

        assertThat(next.bundleDigest()).isNotEqualTo(first.bundleDigest());
        assertThat(next.manifestJson()).contains("\"sourceCommit\":\"" + nextCommit + "\"")
                .contains("contentDigest");
        assertThat(next.changeSummaryJson()).contains("\"agentsChanged\":false")
                .contains("\"skillsChanged\":false")
                .contains("\"toolsChanged\":false")
                .contains("\"dependenciesChanged\":false");
        assertThat(next.requiresRestart()).isFalse();
    }

    @Test
    void rejectsUndeclaredToolDependency() throws Exception {
        Fixture fixture = fixture();
        Files.writeString(fixture.config().resolve("tools/demo.ts"), "import axios from 'axios'\nexport default axios\n");
        var builder = new PublicClientCapabilityPackageBuilder(objectMapper);

        assertThatThrownBy(() -> builder.build(
                fixture.config(), fixture.lock(), fixture.nodeModules(), COMMIT, null, Instant.now()))
                .isInstanceOfSatisfying(
                        PublicClientCapabilityPackageBuilder.CompatibilityException.class,
                        error -> assertThat(error.errorCode()).isEqualTo("TOOL_DEPENDENCY_UNDECLARED"));
    }

    @Test
    void rejectsNativeExtensionInPortableDependency() throws Exception {
        Fixture fixture = fixture();
        Files.write(fixture.nodeModules().resolve("@opencode-ai/plugin/native.node"), new byte[] {1, 2, 3});
        var builder = new PublicClientCapabilityPackageBuilder(objectMapper);

        assertThatThrownBy(() -> builder.build(
                fixture.config(), fixture.lock(), fixture.nodeModules(), COMMIT, null, Instant.now()))
                .isInstanceOfSatisfying(
                        PublicClientCapabilityPackageBuilder.CompatibilityException.class,
                        error -> assertThat(error.errorCode()).isEqualTo("TOOL_DEPENDENCY_NATIVE_BINARY"));
    }

    @Test
    void allowsPrivateAddressParameterExampleButRejectsHardcodedEndpoint() throws Exception {
        Fixture fixture = fixture();
        var builder = new PublicClientCapabilityPackageBuilder(objectMapper);
        Files.writeString(fixture.config().resolve("tools/demo.ts"), """
                import { tool } from '@opencode-ai/plugin'
                const example = '目标 URL，如 http://192.168.1.100:8080/health'
                export default tool({ description: example })
                """);
        assertThat(builder.build(
                fixture.config(), fixture.lock(), fixture.nodeModules(), COMMIT, null, Instant.now()).artifact())
                .isNotEmpty();

        Files.writeString(fixture.config().resolve("tools/demo.ts"), """
                import { tool } from '@opencode-ai/plugin'
                const serverUrl = 'http://192.168.1.100:8080/internal'
                export default tool({ serverUrl })
                """);
        assertThatThrownBy(() -> builder.build(
                fixture.config(), fixture.lock(), fixture.nodeModules(), COMMIT, null, Instant.now()))
                .isInstanceOfSatisfying(
                        PublicClientCapabilityPackageBuilder.CompatibilityException.class,
                        error -> assertThat(error.errorCode()).isEqualTo("SENSITIVE_CONTENT"));
    }

    @Test
    void scansPythonCompanionFilesForSecretsBeforePackaging() throws Exception {
        Fixture fixture = fixture();
        Files.writeString(
                fixture.config().resolve("tools/helper.py"),
                "client_secret = 'enterprise-secret-value'\n");
        var builder = new PublicClientCapabilityPackageBuilder(objectMapper);

        assertThatThrownBy(() -> builder.build(
                fixture.config(), fixture.lock(), fixture.nodeModules(), COMMIT, null, Instant.now()))
                .isInstanceOfSatisfying(
                        PublicClientCapabilityPackageBuilder.CompatibilityException.class,
                        error -> assertThat(error.errorCode()).isEqualTo("SENSITIVE_CONTENT"));
    }

    private Fixture fixture() throws Exception {
        Path config = temporary.resolve("config");
        Files.createDirectories(config.resolve("agents"));
        Files.createDirectories(config.resolve("skills/demo"));
        Files.createDirectories(config.resolve("tools/node_modules/ignored"));
        Files.writeString(config.resolve("agents/public.md"), "---\ndescription: public\n---\npublic agent\n");
        Files.writeString(config.resolve("agents/AGENTS.md"), "excluded");
        Files.writeString(config.resolve("skills/demo/SKILL.md"), "---\nname: demo\n---\ndemo\n");
        Files.writeString(config.resolve("tools/demo.ts"),
                "import { tool } from '@opencode-ai/plugin'\nexport default tool({})\n");
        Files.writeString(config.resolve("tools/package.json"),
                "{\"type\":\"module\",\"dependencies\":{\"@opencode-ai/plugin\":\"1.18.4\"}}");
        Files.writeString(config.resolve("opencode.jsonc"), "{\"provider\":\"secret\"}");

        Path nodeModules = temporary.resolve("runtime/node_modules");
        Files.createDirectories(nodeModules.resolve("@opencode-ai/plugin"));
        Files.writeString(nodeModules.resolve("@opencode-ai/plugin/package.json"),
                "{\"name\":\"@opencode-ai/plugin\",\"version\":\"1.18.4\"}");
        Files.writeString(nodeModules.resolve("@opencode-ai/plugin/index.js"), "export const tool = value => value\n");
        Path lock = temporary.resolve("runtime/package-lock.json");
        Files.writeString(lock,
                "{\"lockfileVersion\":3,\"packages\":{\"\":{\"dependencies\":{\"@opencode-ai/plugin\":\"1.18.4\"}},"
                        + "\"node_modules/@opencode-ai/plugin\":{\"version\":\"1.18.4\"}}}");
        return new Fixture(config, lock, nodeModules);
    }

    private static LocalClientPublicCapabilityModels.Release release(
            PublicClientCapabilityPackageBuilder.BuildResult result,
            Instant createdAt) {
        return new LocalClientPublicCapabilityModels.Release(
                result.sourceCommit(), result.bundleDigest(), result.artifactSha256(),
                LocalClientPublicCapabilityModels.Compatibility.AVAILABLE, null,
                result.manifestJson(), result.changeSummaryJson(), result.counts(), result.requiresRestart(),
                result.artifact(), result.artifact().length, result.uncompressedSize(), result.fileCount(), createdAt);
    }

    private record Fixture(Path config, Path lock, Path nodeModules) {
    }
}
