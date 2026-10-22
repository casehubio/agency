package io.casehub.agency.orchestration.routing;

import java.util.Set;

public interface StageGate {
    Set<String> allStagedBindings();
    Set<String> activeStagedBindings();
}
