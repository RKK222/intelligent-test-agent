package com.icbc.testagent.opencode.runtime.run.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.session.SessionMessageRole;
import org.junit.jupiter.api.Test;

class RunConversationSummarizerTest {

    private final RunConversationSummarizer summarizer = new RunConversationSummarizer();

    @Test
    void createsCompleteDualSummaryWithStableMetadata() {
        RunConversationSummary summary = summarizer.summarize("生成登录回归用例", "已生成并校验登录回归用例。");

        assertThat(summary.user()).satisfies(message -> {
            assertThat(message.role()).isEqualTo(SessionMessageRole.USER);
            assertThat(message.content()).isEqualTo("生成登录回归用例");
            assertThat(message.contentKind()).isEqualTo(RunSummaryContentKind.SUMMARY);
            assertThat(message.summaryStatus()).isEqualTo(RunSummaryStatus.COMPLETE);
            assertThat(message.summaryVersion()).isEqualTo(RunConversationSummarizer.SUMMARY_VERSION);
        });
        assertThat(summary.assistant()).satisfies(message -> {
            assertThat(message.role()).isEqualTo(SessionMessageRole.ASSISTANT);
            assertThat(message.content()).isEqualTo("已生成并校验登录回归用例。");
            assertThat(message.contentKind()).isEqualTo(RunSummaryContentKind.SUMMARY);
            assertThat(message.summaryStatus()).isEqualTo(RunSummaryStatus.COMPLETE);
            assertThat(message.summaryVersion()).isEqualTo(RunConversationSummarizer.SUMMARY_VERSION);
        });
    }

    @Test
    void truncatesUserAndAssistantByUnicodeCodePointsWithoutSplittingEmoji() {
        String user = "😀".repeat(511) + "AB";
        String assistant = "界".repeat(2_001);

        RunConversationSummary summary = summarizer.summarize(user, assistant);

        assertThat(summary.user().content().codePointCount(0, summary.user().content().length())).isEqualTo(512);
        assertThat(summary.user().content()).isEqualTo("😀".repeat(511) + "…");
        assertThat(summary.user().summaryStatus()).isEqualTo(RunSummaryStatus.PARTIAL);
        assertThat(summary.assistant().content().codePointCount(0, summary.assistant().content().length())).isEqualTo(2_000);
        assertThat(summary.assistant().content()).endsWith("…");
        assertThat(summary.assistant().summaryStatus()).isEqualTo(RunSummaryStatus.PARTIAL);
    }

    @Test
    void removesContextReasoningToolAttachmentDataUrlAndControlCharacters() {
        String user = """
                保留用户目标
                <context>内部上下文 secret-context</context>
                <attachment_body>附件正文 secret-attachment</attachment_body>
                图片 data:image/png;base64,aW1hZ2Utc2VjcmV0
                """ + (char) 0 + (char) 7;
        String assistant = """
                保留最终结论
                <reasoning>思维链 secret-reasoning</reasoning>
                <tool_input>{\"command\":\"secret-command\"}</tool_input>
                <tool-output>secret-output</tool-output>
                ```reasoning
                secret-fenced-reasoning
                ```
                {"reasoning":"secret-json-reasoning"}
                """;

        RunConversationSummary summary = summarizer.summarize(user, assistant);

        assertThat(summary.user().content()).contains("保留用户目标");
        assertThat(summary.assistant().content()).contains("保留最终结论");
        assertThat(summary.user().content())
                .doesNotContain(
                        "secret-context",
                        "secret-attachment",
                        "aW1hZ2Utc2VjcmV0",
                        "data:image",
                        Character.toString(0),
                        Character.toString(7));
        assertThat(summary.assistant().content())
                .doesNotContain(
                        "secret-reasoning",
                        "secret-command",
                        "secret-output",
                        "secret-fenced-reasoning",
                        "secret-json-reasoning");
        assertThat(summary.user().summaryStatus()).isEqualTo(RunSummaryStatus.COMPLETE);
        assertThat(summary.assistant().summaryStatus()).isEqualTo(RunSummaryStatus.COMPLETE);
    }

    @Test
    void redactsCredentialPatternsWithoutPersistingTheirValues() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature123456";
        String source = "password=hunter2 token: tok_live_123456 Authorization: Bearer bearer-secret-123 "
                + "api_key=api-secret-123 sk-proj-abcdefghijklmnop ghp_abcdefghijklmnop "
                + "AKIAABCDEFGHIJKLMNOP " + jwt;

        RunConversationSummary summary = summarizer.summarize(source, source);

        assertThat(summary.user().content()).contains("[REDACTED]");
        assertThat(summary.user().content()).doesNotContain(
                "hunter2",
                "tok_live_123456",
                "bearer-secret-123",
                "api-secret-123",
                "sk-proj-abcdefghijklmnop",
                "ghp_abcdefghijklmnop",
                "AKIAABCDEFGHIJKLMNOP",
                jwt);
        assertThat(summary.assistant().content()).isEqualTo(summary.user().content());
    }

    @Test
    void redactsQuotedJsonAndPrefixedEnvironmentCredentialAssignments() {
        String source = "{\"password\":\"hunter2\", \"client_secret\": \"client-value\"} "
                + "DB_PASSWORD=hunter3 SERVICE_API_KEY='api-value'";

        RunConversationSummary summary = summarizer.summarize(source, source);

        assertThat(summary.user().content())
                .contains("[REDACTED]")
                .doesNotContain("hunter2", "hunter3", "client-value", "api-value");
        assertThat(summary.assistant().content()).isEqualTo(summary.user().content());
    }

    @Test
    void returnsRoleSpecificFallbackWhenInputIsEmptyOrOnlyProtectedContent() {
        RunConversationSummary summary = summarizer.summarize(
                " " + (char) 0 + System.lineSeparator(),
                "<reasoning>only hidden text</reasoning>");

        assertThat(summary.user().content()).isEqualTo("用户请求摘要不可用");
        assertThat(summary.user().summaryStatus()).isEqualTo(RunSummaryStatus.FALLBACK);
        assertThat(summary.assistant().content()).isEqualTo("助手回答摘要不可用");
        assertThat(summary.assistant().summaryStatus()).isEqualTo(RunSummaryStatus.FALLBACK);
    }

    @Test
    void convertsSanitizerFailureToFallbackWithoutLeakingOriginalText() {
        RunConversationSummarizer failing = new RunConversationSummarizer(text -> {
            if (text.contains("secret-assistant")) {
                throw new IllegalStateException("secret-original-value");
            }
            return text;
        });

        RunConversationSummary summary = failing.summarize("visible-user", "secret-assistant");

        assertThat(summary.user().content()).isEqualTo("visible-user");
        assertThat(summary.assistant().content()).isEqualTo("助手回答摘要不可用");
        assertThat(summary.user().summaryStatus()).isEqualTo(RunSummaryStatus.COMPLETE);
        assertThat(summary.assistant().summaryStatus()).isEqualTo(RunSummaryStatus.FALLBACK);
        assertThat(summary.user().content()).doesNotContain("secret-original-value");
        assertThat(summary.assistant().content()).doesNotContain("secret-original-value", "secret-assistant");
    }
}
