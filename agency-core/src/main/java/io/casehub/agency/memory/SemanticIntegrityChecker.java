package io.casehub.agency.memory;

import java.util.List;

@FunctionalInterface
public interface SemanticIntegrityChecker {
    List<IntegrityViolation> checkSemantic(List<IntegrityViolation> flagged,
                                            String agentId, String tenantId);
}
