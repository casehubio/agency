package io.casehub.agency.orchestration.bridging;

public record RoleBridgingPolicy(
    Mode mode,
    double minConfidence,
    double demotionConfidence,
    int minHoldCycles) {

  public static final RoleBridgingPolicy ADVISORY =
      new RoleBridgingPolicy(Mode.ADVISORY, 0.0, 0.0, 0);

  public static final RoleBridgingPolicy DISABLED =
      new RoleBridgingPolicy(Mode.ADVISORY, 1.1, 0.0, 0);

  public RoleBridgingPolicy {
    if (mode != Mode.ADVISORY && demotionConfidence >= minConfidence) {
      throw new IllegalArgumentException(
          "demotionConfidence (" + demotionConfidence
              + ") must be lower than minConfidence (" + minConfidence
              + ") for hysteresis");
    }
    if (minHoldCycles < 0) {
      throw new IllegalArgumentException("minHoldCycles must be >= 0");
    }
  }

  public enum Mode {
    ADVISORY,
    AUTO_ABOVE_THRESHOLD,
    AUTOMATIC
  }
}
