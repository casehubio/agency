package io.casehub.agency.deliberation.normative;

public enum NormSpecificity {
    UNIVERSAL,
    DOMAIN,
    TENANT,
    CASE_TYPE,
    INSTANCE;

    public boolean isMoreSpecificThan(NormSpecificity other) {
        return this.ordinal() > other.ordinal();
    }
}
