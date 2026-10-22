package io.casehub.agency.governance.trust;

public interface VouchConstraint {
    VouchEligibility check(VouchRequest request);
}
