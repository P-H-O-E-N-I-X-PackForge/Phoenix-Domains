package net.phoenixvine.domains.integration.xaero;

import net.minecraftforge.fml.ModList;

public final class DomainsXaeroWorldMapIntegration {

    public static final String XAERO_WORLDMAP_MOD_ID = "xaeroworldmap";

    private DomainsXaeroWorldMapIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(XAERO_WORLDMAP_MOD_ID);
    }
}
