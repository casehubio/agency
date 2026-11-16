package io.casehub.agency.orchestration.bridging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

class RoleBridgingPolicyTest {

  @Test
  void advisory_constant_has_advisory_mode() {
    assertThat(RoleBridgingPolicy.ADVISORY.mode()).isEqualTo(RoleBridgingPolicy.Mode.ADVISORY);
  }

  @Test
  void disabled_constant_has_unreachable_threshold() {
    assertThat(RoleBridgingPolicy.DISABLED.minConfidence()).isGreaterThan(1.0);
  }

  @Test
  void advisory_mode_allows_equal_thresholds() {
    assertThatNoException().isThrownBy(() ->
        new RoleBridgingPolicy(RoleBridgingPolicy.Mode.ADVISORY, 0.5, 0.5, 0));
  }

  @Test
  void auto_above_threshold_requires_hysteresis() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RoleBridgingPolicy(
            RoleBridgingPolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.8, 5))
        .withMessageContaining("demotionConfidence")
        .withMessageContaining("minConfidence");
  }

  @Test
  void auto_above_threshold_accepts_proper_hysteresis() {
    assertThatNoException().isThrownBy(() ->
        new RoleBridgingPolicy(RoleBridgingPolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.5, 3));
  }

  @Test
  void automatic_requires_hysteresis() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RoleBridgingPolicy(
            RoleBridgingPolicy.Mode.AUTOMATIC, 0.6, 0.7, 2))
        .withMessageContaining("demotionConfidence")
        .withMessageContaining("minConfidence");
  }

  @Test
  void negative_hold_cycles_rejected() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RoleBridgingPolicy(
            RoleBridgingPolicy.Mode.ADVISORY, 0.5, 0.3, -1))
        .withMessageContaining("minHoldCycles");
  }

  @Test
  void zero_hold_cycles_accepted() {
    assertThatNoException().isThrownBy(() ->
        new RoleBridgingPolicy(RoleBridgingPolicy.Mode.AUTO_ABOVE_THRESHOLD, 0.8, 0.3, 0));
  }
}
