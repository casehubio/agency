package io.casehub.agency.trust;

public interface VouchConstraint {
    VouchEligibility check(VouchRequest request);
}
