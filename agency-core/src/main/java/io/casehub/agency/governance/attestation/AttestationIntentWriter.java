package io.casehub.agency.governance.attestation;

public interface AttestationIntentWriter {
    void write(AttestationIntent intent, String tenancyId);
}
