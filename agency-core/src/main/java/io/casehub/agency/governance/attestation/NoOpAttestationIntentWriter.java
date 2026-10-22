package io.casehub.agency.governance.attestation;

public class NoOpAttestationIntentWriter implements AttestationIntentWriter {

    @Override
    public void write(AttestationIntent intent, String tenancyId) {
    }
}
