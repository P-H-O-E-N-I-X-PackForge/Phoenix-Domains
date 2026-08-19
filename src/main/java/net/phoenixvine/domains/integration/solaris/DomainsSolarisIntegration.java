package net.phoenixvine.domains.integration.solaris;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/**
 * Optional integration with Solaris. {@code phoenix_solaris} is declared as a
 * non-mandatory, client-only {@code mods.toml} dependency, so nothing in this class
 * may be touched unless {@link #isAvailable()} is true first.
 *
 * This class uses a nested lazy-loaded helper class ({@link Impl}) to prevent
 * the JVM from attempting to resolve Solaris classes when the mod is missing.
 */
public final class DomainsSolarisIntegration {

    public static final String SOLARIS_MOD_ID = "solaris";
    public static final String FEATURE_CLAIM_MAP = "domains_claim_map";

    private static boolean registered = false;

    private DomainsSolarisIntegration() {}

    /**
     * Safe to call anywhere. Does not trigger loading of any Solaris classes.
     */
    public static boolean isAvailable() {
        return ModList.get().isLoaded(SOLARIS_MOD_ID);
    }

    /** Call only after {@link #isAvailable()} has returned true. Idempotent. */
    public static void init() {
        if (!isAvailable()) return;
        if (registered) return;
        registered = true;
        Impl.init();
    }

    /** Call only after {@link #isAvailable()} has returned true. */
    public static void requestRefresh() {
        if (isAvailable()) {
            Impl.requestRefresh();
        }
    }

    /**
     * Call only after {@link #isAvailable()} has returned true. Equivalent to
     * {@link #openClaimMapScreen(Screen)} with {@code null} — i.e. nothing to return to.
     */
    public static Screen openClaimMapScreen() {
        return openClaimMapScreen(null);
    }

    /**
     * Call only after {@link #isAvailable()} has returned true. {@code returnTo} is threaded
     * through to the resulting {@link SolarisClaimMapScreen} so it can hand the player back to
     * {@code returnTo} on close instead of dropping to the world.
     */
    public static Screen openClaimMapScreen(Screen returnTo) {
        if (!isAvailable()) {
            throw new IllegalStateException("Cannot open Solaris map: Solaris is not installed!");
        }
        return Impl.openClaimMapScreen(returnTo);
    }

    /**
     * Safely retrieves the feature state mapped to a local enum.
     * Safe to call even if Solaris is not installed (returns {@link FeatureState#DISABLED}).
     */
    public static FeatureState claimMapState(ResourceLocation dimension) {
        if (!isAvailable()) {
            return FeatureState.DISABLED;
        }
        return Impl.claimMapState(dimension);
    }

    /**
     * A safe local enum mirror of Solaris's state system so calling classes
     * don't have to import or load Solaris classes.
     */
    public enum FeatureState {

        DISABLED,
        VISIBLE,
        ENABLED;

        public boolean atLeast(FeatureState other) {
            return this.ordinal() >= other.ordinal();
        }
    }

    /**
     * Nested implementation helper. The JVM will only load this class and resolve
     * its Solaris-specific imports/types if it is explicitly executed.
     */
    private static class Impl {

        private static void init() {
            net.phoenixvine.solaris.api.SolarisAPI.registerOverlay(new DomainClaimOverlay());
        }

        private static void requestRefresh() {
            net.phoenixvine.solaris.api.SolarisAPI.requestRefresh();
        }

        private static Screen openClaimMapScreen(Screen returnTo) {
            return new SolarisClaimMapScreen(returnTo);
        }

        private static FeatureState claimMapState(ResourceLocation dimension) {
            net.phoenixvine.solaris.api.SolarisFeatureState state = net.phoenixvine.solaris.api.SolarisAPI
                    .getFeatureState(FEATURE_CLAIM_MAP, dimension);

            if (state == null) return FeatureState.DISABLED;
            switch (state) {
                case ENABLED:
                    return FeatureState.ENABLED;
                case VISIBLE:
                    return FeatureState.VISIBLE;
                default:
                    return FeatureState.DISABLED;
            }
        }
    }
}
