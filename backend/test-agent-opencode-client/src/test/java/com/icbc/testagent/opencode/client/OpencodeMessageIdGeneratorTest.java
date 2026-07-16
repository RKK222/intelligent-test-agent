package com.icbc.testagent.opencode.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OpencodeMessageIdGeneratorTest {

    private static final long FAILURE_TIMESTAMP_MILLIS = 1_784_172_642_847L;
    private static final String FIXED_SUFFIX = "00000000000000";

    @Test
    void generatesOpencodeCompatibleMessageId() {
        String messageId = new OpencodeMessageIdGenerator().nextId();

        assertThat(messageId).matches("msg_[0-9a-f]{12}[0-9A-Za-z]{14}");
    }

    @Test
    void incrementsTimeSequenceWithinSameMillisecond() {
        OpencodeMessageIdGenerator generator = fixedGenerator(FAILURE_TIMESTAMP_MILLIS);

        assertThat(generator.nextId()).isEqualTo("msg_f68fa021f001" + FIXED_SUFFIX);
        assertThat(generator.nextId()).isEqualTo("msg_f68fa021f002" + FIXED_SUFFIX);
    }

    @Test
    void remainsStrictlyIncreasingWhenClockMovesBackward() {
        AtomicLong clock = new AtomicLong(FAILURE_TIMESTAMP_MILLIS + 1);
        OpencodeMessageIdGenerator generator = new OpencodeMessageIdGenerator(
                clock::getAndDecrement,
                () -> FIXED_SUFFIX);

        List<String> messageIds = IntStream.range(0, 4)
                .mapToObj(ignored -> generator.nextId())
                .toList();

        assertThat(messageIds).isSortedAccordingTo(String::compareTo);
        assertThat(new HashSet<>(messageIds)).hasSize(messageIds.size());
    }

    @Test
    void generatesUniqueIdsUnderConcurrentCalls() {
        OpencodeMessageIdGenerator generator = fixedGenerator(FAILURE_TIMESTAMP_MILLIS);

        List<String> messageIds = IntStream.range(0, 2_000)
                .parallel()
                .mapToObj(ignored -> generator.nextId())
                .toList();

        assertThat(new HashSet<>(messageIds)).hasSize(messageIds.size());
        assertThat(messageIds).allMatch(id -> id.matches("msg_[0-9a-f]{12}[0-9A-Za-z]{14}"));
    }

    @Test
    void realFailureSamplePlacesGeneratedUserMessageAfterPreviousAssistant() {
        String previousAssistantMessageId = "msg_f68f9760b001bRCW2uenAHEuGV";
        String brokenRandomUserMessageId = "msg_e04e58dcc9e0408f974921303fdd680a";

        String generatedUserMessageId = fixedGenerator(FAILURE_TIMESTAMP_MILLIS).nextId();

        assertThat(brokenRandomUserMessageId).isLessThan(previousAssistantMessageId);
        assertThat(generatedUserMessageId).isGreaterThan(previousAssistantMessageId);
    }

    private static OpencodeMessageIdGenerator fixedGenerator(long timestampMillis) {
        return new OpencodeMessageIdGenerator(() -> timestampMillis, () -> FIXED_SUFFIX);
    }
}
