package net.phoenixvine.domains.integration.xaero;

import net.minecraftforge.fml.ModList;

/**
 * Optional integration with Xaero's Minimap. {@code xaerominimap} is declared as a non-mandatory
 * {@code mods.toml} dependency, so this class must contain ONLY the availability check — zero
 * imports or references to anything in {@code xaero.*}, same reasoning as
 * {@code DomainsChroniclesIntegration}. Xaero's Minimap and Xaero's World Map are separate mods
 * with separate jars/modids (confirmed against the real downloaded jars' own {@code mods.toml}
 * entries) — this gatekeeper is minimap-only; see {@link DomainsXaeroWorldMapIntegration} for the
 * fullscreen-map side.
 */
public final class DomainsXaeroMinimapIntegration {

    public static final String XAERO_MINIMAP_MOD_ID = "xaerominimap";

    private DomainsXaeroMinimapIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(XAERO_MINIMAP_MOD_ID);
    }
}
