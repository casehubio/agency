package io.casehub.agency.attestation;

public interface AttestationIntentWriter {
    void write(AttestationIntent intent, String tenancyId);
}
