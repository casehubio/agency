package io.casehub.agency.governance.trust;

public sealed interface VouchEligibility {
    record Eligible() implements VouchEligibility {}
    record Ineligible(String reason) implements VouchEligibility {}
}
