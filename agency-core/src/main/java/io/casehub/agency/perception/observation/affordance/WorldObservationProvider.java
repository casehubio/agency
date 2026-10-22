package io.casehub.agency.perception.observation.affordance;

import java.util.List;

@FunctionalInterface
public interface WorldObservationProvider {

    List<ObservationSection> worldSections();
}
