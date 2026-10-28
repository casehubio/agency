package io.casehub.agency.deliberation.protocol;

import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProtocolContext;

import java.util.List;

@FunctionalInterface
public interface ProtocolStateEvaluator {
    List<DispatchAdvisory> evaluate(ProtocolContext context);
}
