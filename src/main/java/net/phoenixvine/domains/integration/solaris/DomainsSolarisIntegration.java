package net.phoenixvine.domains.integration.solaris;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

public final class DomainsSolarisIntegration {

    public static final String SOLARIS_MOD_ID = "solaris";
    public static final String FEATURE_CLAIM_MAP = "domains_claim_map";

    private static boolean registered = false;

    private DomainsSolarisIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(SOLARIS_MOD_ID);
    }

    public static void init() {
        if (!isAvailable()) return;
        if (registered) return;
        registered = true;
        Impl.init();
    }

    public static void requestRefresh() {
        if (isAvailable()) {
            Impl.requestRefresh();
        }
    }

    public static Screen openClaimMapScreen() {
        return openClaimMapScreen(null);
    }

    public static Screen openClaimMapScreen(Screen returnTo) {
        if (!isAvailable()) {
            throw new IllegalStateException("Cannot open Solaris map: Solaris is not installed!");
        }
        return Impl.openClaimMapScreen(returnTo);
    }

    public static FeatureState claimMapState(ResourceLocation dimension) {
        if (!isAvailable()) {
            return FeatureState.DISABLED;
        }
        return Impl.claimMapState(dimension);
    }

    public enum FeatureState {

        DISABLED,
        VISIBLE,
        ENABLED;

        public boolean atLeast(FeatureState other) {
            return this.ordinal() >= other.ordinal();
        }
    }

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
