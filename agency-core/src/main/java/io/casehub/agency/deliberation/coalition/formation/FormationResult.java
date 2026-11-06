package io.casehub.agency.deliberation.coalition.formation;

public sealed interface FormationResult {

    record Accepted(String groupInstanceId) implements FormationResult {}

    record Rejected(String reason) implements FormationResult {}

    record NegotiationFailed(String reason) implements FormationResult {}

    record Timeout() implements FormationResult {}

    static FormationResult accepted(String groupInstanceId) {
        return new Accepted(groupInstanceId);
    }

    static FormationResult rejected(String reason) {
        return new Rejected(reason);
    }

    static FormationResult negotiationFailed(String reason) {
        return new NegotiationFailed(reason);
    }

    static FormationResult timeout() {
        return new Timeout();
    }
}
