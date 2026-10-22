package io.casehub.agency.annotations.deployment;

import io.casehub.agency.attestation.AttestationContext;
import io.casehub.agency.attestation.AttestationIntent;
import io.casehub.agency.attestation.LifecycleAttestationObserver;

import java.util.List;

class TestAttestationObserver implements LifecycleAttestationObserver<String> {
    @Override
    public List<AttestationIntent> observe(String event, AttestationContext context) {
        return List.of();
    }
}
