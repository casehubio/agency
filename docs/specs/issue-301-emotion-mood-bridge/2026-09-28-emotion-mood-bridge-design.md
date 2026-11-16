# Emotion → Mood Bridge — Goal Emotions Influence Agent Mood

**Issue:** casehubio/blocks#301
**Epic:** casehubio/blocks#298 (Emotion architecture)

## Context

Goal-level OCC emotions (Fear, Hope, Satisfaction, etc.) are computed by `CognitiveGoalOrchestrator` and rendered in `EmergentGoalPromptSection`, but they don't influence the agent's overall mood. An agent can show [CONCERNED] on a goal while sounding cheerful everywhere else. This bridges ALMA Layer 1 (Emotion) → Layer 2 (Mood).

Each `CognitiveEmotion` already carries a `PadProjection` with pleasure/arousal/dominance values — no OCC→PAD mapping is needed. The bridge aggregates these existing PAD values and feeds them to `MoodOrchestrator.record()`.

## Design

### GoalEmotionMoodBridge

A new `CognitionTickParticipant` registered in the TERMINAL phase. It runs after `CognitiveGoalOrchestrator` ticks (both are TERMINAL participants; registration order determines execution order).

**Constructor:** `GoalEmotionMoodBridge(CognitiveGoalOrchestrator goalOrchestrator, MoodOrchestrator moodOrchestrator)`

**`tick(CognitionTickContext context)`:**
1. Call `goalOrchestrator.currentState(agentId, tenantId)`
2. If empty or no goal emotions, return (no mood signal)
3. Flatten all `CognitiveEmotion` instances across all `GoalEmotion` entries
4. If empty after flattening, return
5. Compute intensity-weighted average PAD:
   - `weightedPleasure = Σ(emotion.intensity() × emotion.pad().pleasure()) / Σ(emotion.intensity())`
   - Same for arousal and dominance
6. Create `MoodSignal.DirectShift(weightedPleasure, weightedArousal, weightedDominance, "goal-emotions")`
7. Call `moodOrchestrator.record(signal, agentId, tenantId)`

The cause string `"goal-emotions"` enables traceability in mood debugging.

### Wiring

**BlocksBeans:**
- Produce `GoalEmotionMoodBridge` as `@ApplicationScoped` bean (takes `CognitiveGoalOrchestrator` and `MoodOrchestrator`)
- Register via `CognitionCore.addParticipant(CognitionPhase.TERMINAL, bridge)` — must be registered after the goal orchestrator so it runs second

**BlocksAutoConfiguration:**
- Spring equivalent — `@Bean` producer, same registration

### Phase Ordering

Both `CognitiveGoalOrchestrator` and `GoalEmotionMoodBridge` are TERMINAL participants. The bridge must run after goals. `CognitionCore.addParticipant()` appends to the participant list, so registration order = execution order. The CDI/Spring wiring must register the goal orchestrator before the bridge.

### Edge Cases

- **No goal orchestrator present:** If `CognitiveGoalOrchestrator` is not available (optional dependency), the bridge is not produced. Guard in CDI with `Instance<CognitiveGoalOrchestrator>`.
- **Zero-intensity emotions:** If all intensities are zero, the weighted average would divide by zero. Guard: if total intensity ≤ 0, skip.
- **PAD values out of range:** `PadProjection` values come from the OCC model and may exceed [-2,2]. `MoodOrchestrator` clamps internally, so no clamping needed here.

## Testing

- **GoalEmotionMoodBridgeTest:**
  - `tickWithEmotionsProducesWeightedAverageDirectShift` — two emotions with known intensities and PAD values, verify the MoodSignal has correct weighted average
  - `tickWithNoGoalStateProducesNoSignal` — currentState returns empty, verify no record() call
  - `tickWithNoEmotionsProducesNoSignal` — goals present but no emotions, verify no record() call
  - `tickWithZeroIntensitySkips` — all intensities zero, verify no record() call
  - `multipleGoalsAggregateAcrossAll` — emotions from different goals are all included in the average

## References

- MoodOrchestrator.java (blocks-core) — `record(MoodSignal, agentId, tenantId)`, `MoodSignal.DirectShift`
- CognitiveGoalOrchestrator.java (blocks-core) — `currentState()`, `CognitiveGoalState`, `GoalEmotion`
- CognitiveEmotion.java (blocks-core) — `intensity()`, `pad()` → `PadProjection`
- PadProjection.java (blocks-core) — `pleasure()`, `arousal()`, `dominance()`
- CognitionCore.java:493-498 — `addParticipant(CognitionPhase, CognitionTickParticipant)`
- casehubio/blocks#298 — parent epic (emotion architecture)
