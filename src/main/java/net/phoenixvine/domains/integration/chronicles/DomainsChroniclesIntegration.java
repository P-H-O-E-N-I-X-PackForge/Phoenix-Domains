package net.phoenixvine.domains.integration.chronicles;

import net.minecraftforge.fml.ModList;

public final class DomainsChroniclesIntegration {

    public static final String CHRONICLES_MOD_ID = "phoenix_chronicles";

    private DomainsChroniclesIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(CHRONICLES_MOD_ID);
    }
}
