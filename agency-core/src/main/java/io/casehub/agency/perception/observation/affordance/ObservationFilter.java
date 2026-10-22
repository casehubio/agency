package io.casehub.agency.perception.observation.affordance;

import java.util.List;
import java.util.Set;

@FunctionalInterface
public interface ObservationFilter {
    List<ObservationSection> filter(List<ObservationSection> sections,
                                     Set<String> observerTags);
}
