## D1: Naming — rename existing ConversationProtocol constants class

**Choice:** Rename `ConversationProtocol` → `ConversationConstants`, freeing the name for the new `ChannelProtocol` implementation
**Alternatives:**
- Name the new class `ConversationChannelProtocol` — inconsistent with `NegotiationProtocol` naming
- Same name in `.protocol` sub-package — confusing same-name classes in parent/child packages
**Rationale:** Consistent naming with established NegotiationProtocol pattern. The constants class is internal to the conversation package.
**Trade-offs:** One rename across existing consumers (DraftHouse uses it)
**Sources:** `ConversationProtocol.java` (existing constants), `NegotiationProtocol.java` (naming pattern)
**Exploration:** quick
**Status:** captured

## D2: ConversationProjection instantiation

**Choice:** Create `DefaultConversationProjection` concrete subclass in `.protocol` sub-package with sensible defaults
**Alternatives:**
- Make `ConversationProjection` non-abstract — breaks existing subclasses (DebateChannelProjection in DraftHouse)
- Pass hooks via constructor lambdas — over-engineered for a default
**Rationale:** ConversationProjection is abstract with domain-specific hooks. A default concrete subclass preserves the extension model while giving the protocol a usable projection.
**Trade-offs:** One more class, but it's small and focused
**Sources:** `ConversationProjection.java` (abstract hooks), DraftHouse `DebateChannelProjection` (existing subclass)
**Exploration:** quick
**Status:** captured

## D3: JudgmentProtocol agreement evaluation — direct logic vs AgreementPolicy SPI

**Choice:** Implement quorum logic directly in `JudgmentRules` (count approvals vs rejections, 3 switch cases) rather than wrapping verdicts into `JudgmentResponse` for `AgreementPolicy`
**Alternatives:**
- Wrap `JudgmentVerdict` → `JudgmentResponse` and delegate to `ConsensusAgreement` — couples lightweight channel verdicts to heavyweight execution types (`Evidence`, `CallerIdentity`)
**Rationale:** The quorum logic is simple (unanimous = all approve, majority = >50%, consensus = ≥2/3). Wrapping introduces unnecessary coupling and adapter boilerplate. The `AgreementPolicy` SPI remains available for the heavier execution-driver judgment path.
**Trade-offs:** Minor duplication of quorum arithmetic (3 cases). If agreement logic grows complex, would need to factor out shared logic.
**Sources:** `ConsensusAgreement.java`, `AgreementPolicy.java`, `JudgmentResponse.java`
**Exploration:** quick
**Status:** captured

## D4: Both protocols follow the NegotiationProtocol two-class pattern

**Choice:** Exact same pattern — `*Protocol` (@ApplicationScoped + ChannelProjector + cache) and `*Rules` (pure, immutable, Builder, withOverrides)
**Alternatives:**
- None seriously considered — the pattern was designed for exactly this replication
**Rationale:** Consistency across all ChannelProtocol implementations. Proven caching and policyOverrides mechanics.
**Trade-offs:** None
**Sources:** `NegotiationProtocol.java`, `NegotiationRules.java`
**Exploration:** quick
**Status:** captured
