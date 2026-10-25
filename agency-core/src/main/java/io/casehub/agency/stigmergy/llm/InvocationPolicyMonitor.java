package io.casehub.agency.stigmergy.llm;

import java.time.Duration;
import java.util.logging.Logger;

final class InvocationPolicyMonitor {

    private static final Logger LOG = Logger.getLogger(InvocationPolicyMonitor.class.getName());

    private InvocationPolicyMonitor() {}

    static boolean check(LlmInvocationPolicy policy, Duration actual, String phase) {
        if (actual.compareTo(policy.errorAfter()) > 0) {
            LOG.severe(String.format(
                    "LLM %s invocation exceeded error threshold: %ds (limit: %ds)",
                    phase, actual.toSeconds(), policy.errorAfter().toSeconds()));
            return true;
        }
        if (actual.compareTo(policy.warnAfter()) > 0) {
            LOG.warning(String.format(
                    "LLM %s invocation exceeded warn threshold: %ds (expected: %ds, limit: %ds)",
                    phase, actual.toSeconds(),
                    policy.expectedDuration().toSeconds(), policy.warnAfter().toSeconds()));
        }
        return false;
    }
}
