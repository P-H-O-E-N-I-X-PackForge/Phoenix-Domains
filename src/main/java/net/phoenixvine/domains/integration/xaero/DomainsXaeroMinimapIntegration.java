package net.phoenixvine.domains.integration.xaero;

import net.minecraftforge.fml.ModList;

public final class DomainsXaeroMinimapIntegration {

    public static final String XAERO_MINIMAP_MOD_ID = "xaerominimap";

    private DomainsXaeroMinimapIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(XAERO_MINIMAP_MOD_ID);
    }
}
