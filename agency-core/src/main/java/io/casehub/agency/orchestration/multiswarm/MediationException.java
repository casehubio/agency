package io.casehub.agency.orchestration.multiswarm;

public class MediationException extends RuntimeException {
    public MediationException(String message) {
        super(message);
    }

    public MediationException(String message, Throwable cause) {
        super(message, cause);
    }
}
