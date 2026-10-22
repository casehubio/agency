package io.casehub.agency.social.memory;

import io.casehub.neocortex.memory.MemoryDomain;

import java.util.List;

@FunctionalInterface
public interface IntegrityChecker {
    List<IntegrityViolation> check(String agentId, String tenantId, MemoryDomain domain);
}
