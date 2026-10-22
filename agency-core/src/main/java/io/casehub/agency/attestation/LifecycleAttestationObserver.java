package io.casehub.agency.attestation;

import java.util.List;

@FunctionalInterface
public interface LifecycleAttestationObserver<E> {
    List<AttestationIntent> observe(E event, AttestationContext context);
}
