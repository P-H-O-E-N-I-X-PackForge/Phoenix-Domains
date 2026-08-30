package net.phoenixvine.domains.api;

public enum DomainFeatureState {

    DISABLED,
    VISIBLE,
    ENABLED;

    public boolean atLeast(DomainFeatureState minimum) {
        return ordinal() >= minimum.ordinal();
    }
}
