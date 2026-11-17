package io.casehub.agency.orchestration.degradation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CollapsePolicyTest {

    @Test
    void never_alwaysReturnsFalse() {
        var policy = CollapsePolicy.never();
        assertThat(policy.isCollapsed(0, 0L)).isFalse();
        assertThat(policy.isCollapsed(100, 100_000L)).isFalse();
    }

    @Test
    void afterCalls_falseBeforeThreshold() {
        var policy = CollapsePolicy.afterCalls(3);
        assertThat(policy.isCollapsed(0, 0L)).isFalse();
        assertThat(policy.isCollapsed(1, 0L)).isFalse();
        assertThat(policy.isCollapsed(2, 0L)).isFalse();
    }

    @Test
    void afterCalls_trueAtThreshold() {
        var policy = CollapsePolicy.afterCalls(3);
        assertThat(policy.isCollapsed(3, 0L)).isTrue();
    }

    @Test
    void afterCalls_trueAboveThreshold() {
        var policy = CollapsePolicy.afterCalls(3);
        assertThat(policy.isCollapsed(5, 0L)).isTrue();
    }

    @Test
    void afterDuration_falseBeforeThreshold() {
        var policy = CollapsePolicy.afterDuration(java.time.Duration.ofSeconds(10));
        assertThat(policy.isCollapsed(1, 0L)).isFalse();
        assertThat(policy.isCollapsed(1, 9_999L)).isFalse();
    }

    @Test
    void afterDuration_trueAtThreshold() {
        var policy = CollapsePolicy.afterDuration(java.time.Duration.ofSeconds(10));
        assertThat(policy.isCollapsed(1, 10_000L)).isTrue();
    }

    @Test
    void afterDuration_trueAboveThreshold() {
        var policy = CollapsePolicy.afterDuration(java.time.Duration.ofSeconds(10));
        assertThat(policy.isCollapsed(1, 15_000L)).isTrue();
    }

    @Test
    void afterCalls_rejectsZeroThreshold() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CollapsePolicy.afterCalls(0))
                                       .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void afterCalls_rejectsNegativeThreshold() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CollapsePolicy.afterCalls(-1))
                                       .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void afterDuration_rejectsZeroDuration() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CollapsePolicy.afterDuration(java.time.Duration.ZERO))
                                       .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void afterDuration_rejectsNegativeDuration() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CollapsePolicy.afterDuration(java.time.Duration.ofSeconds(-1)))
                                       .isInstanceOf(IllegalArgumentException.class);
    }

}
