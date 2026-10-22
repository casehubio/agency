package io.casehub.agency.social.memory;

import java.util.List;

@FunctionalInterface
public interface SemanticIntegrityChecker {
    List<IntegrityViolation> checkSemantic(List<IntegrityViolation> flagged,
                                            String agentId, String tenantId);
}
