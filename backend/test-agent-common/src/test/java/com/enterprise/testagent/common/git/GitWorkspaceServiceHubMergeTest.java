package com.enterprise.testagent.common.git;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GitWorkspaceServiceHubMergeTest {

    @Test
    void mergesIndependentTextChangesWithoutTouchingARepository() {
        GitWorkspaceService.MergeTextResult result = new GitWorkspaceService().mergeText(
                "title\nbase\ntail\n",
                "title-current\nbase\ntail\n",
                "title\nbase\ntail-incoming\n");

        assertThat(result.conflicted()).isFalse();
        assertThat(result.content()).isEqualTo("title-current\nbase\ntail-incoming\n");
    }

    @Test
    void returnsConflictMarkersForOverlappingChanges() {
        GitWorkspaceService.MergeTextResult result = new GitWorkspaceService().mergeText(
                "same\n", "current\n", "incoming\n");

        assertThat(result.conflicted()).isTrue();
        assertThat(result.content()).contains("<<<<<<< 当前引用", "=======", ">>>>>>> Hub 最新版");
    }
}
