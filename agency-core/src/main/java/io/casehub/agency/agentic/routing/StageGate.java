package io.casehub.agency.agentic.routing;

import java.util.Set;

public interface StageGate {
    Set<String> allStagedBindings();
    Set<String> activeStagedBindings();
}
