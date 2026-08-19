package net.phoenixvine.domains.api;

/** Mirrors Solaris's {@code SolarisFeatureState} — same three-tier gating vocabulary. */
public enum DomainFeatureState {

    DISABLED,
    VISIBLE,
    ENABLED;

    public boolean atLeast(DomainFeatureState minimum) {
        return ordinal() >= minimum.ordinal();
    }
}
