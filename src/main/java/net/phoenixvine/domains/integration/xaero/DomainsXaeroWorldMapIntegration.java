package net.phoenixvine.domains.integration.xaero;

import net.minecraftforge.fml.ModList;

/**
 * Optional integration with Xaero's World Map (the fullscreen map, a separate mod/jar from
 * Xaero's Minimap — see {@link DomainsXaeroMinimapIntegration}). {@code xaeroworldmap} is
 * declared as a non-mandatory {@code mods.toml} dependency, so this class must contain ONLY the
 * availability check — zero imports or references to anything in {@code xaero.*}.
 */
public final class DomainsXaeroWorldMapIntegration {

    public static final String XAERO_WORLDMAP_MOD_ID = "xaeroworldmap";

    private DomainsXaeroWorldMapIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(XAERO_WORLDMAP_MOD_ID);
    }
}
