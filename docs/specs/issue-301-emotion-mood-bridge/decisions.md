## D1: Bridge location

**Choice:** New `CognitionTickParticipant` — a `GoalEmotionMoodBridge` registered in TERMINAL phase that runs after goals
**Alternatives:**
- Inside CognitiveGoalOrchestrator — fewer moving parts but couples mood and goal concerns
- In CognitionCore.tick() directly — simplest but grows CognitionCore further
**Rationale:** The bridge is a distinct concern (translating emotions to mood). CognitionTickParticipant is the existing extension mechanism for TERMINAL phase logic. The bridge takes MoodOrchestrator and CognitiveGoalOrchestrator as dependencies, keeping each focused on its own responsibility.
**Trade-offs:** One more participant to register in CDI/Spring wiring. Acceptable for an S-scale task.
**Sources:** CognitionCore.java (addParticipant, CognitionPhase.TERMINAL), CognitiveGoalOrchestrator (CognitionTickParticipant pattern)
**Exploration:** quick
**Status:** captured

## D2: Emotion aggregation strategy

**Choice:** Intensity-weighted average — weight each CognitiveEmotion's PadProjection by its intensity, average across all emotions
**Alternatives:**
- Sum with clamping — sum PAD deltas scaled by intensity, clamp to [-2,2]. Stronger effect but risk of saturating mood.
- Dominant emotion only — pick highest-intensity emotion. Simple but ignores mixed emotional states.
**Rationale:** Weighted average produces a balanced mood shift reflecting the full emotional landscape without extremes. Mixed emotions (Fear 0.8 + Hope 0.6) produce a nuanced mood rather than swinging to one pole.
**Trade-offs:** Mild emotions get diluted in a large emotion set. Acceptable because intensity weighting already prioritizes strong emotions.
**Sources:** PadProjection (pleasure/arousal/dominance), CognitiveEmotion (intensity field)
**Exploration:** quick
**Status:** captured
