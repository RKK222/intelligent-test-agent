package com.enterprise.testagent.domain.internalmodelobservability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class InternalModelCallOutcomeGroupTest {

    @Test
    void everyDetailedOutcomeBelongsToExactlyOneDashboardGroup() {
        Set<InternalModelCallOutcome> groupedOutcomes = Arrays.stream(InternalModelCallOutcomeGroup.values())
                .flatMap(group -> group.outcomes().stream())
                .collect(Collectors.toSet());
        int groupedCount = Arrays.stream(InternalModelCallOutcomeGroup.values())
                .mapToInt(group -> group.outcomes().size())
                .sum();

        assertThat(groupedOutcomes).containsExactlyInAnyOrder(InternalModelCallOutcome.values());
        assertThat(groupedCount).isEqualTo(InternalModelCallOutcome.values().length);
        for (InternalModelCallOutcome outcome : InternalModelCallOutcome.values()) {
            assertThat(InternalModelCallOutcomeGroup.from(outcome).outcomes()).contains(outcome);
        }
    }
}
