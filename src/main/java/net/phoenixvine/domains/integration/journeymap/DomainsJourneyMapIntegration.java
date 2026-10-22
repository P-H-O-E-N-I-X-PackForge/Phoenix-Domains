package net.phoenixvine.domains.integration.journeymap;

import net.neoforged.fml.ModList;

public final class DomainsJourneyMapIntegration {

    public static final String JOURNEYMAP_MOD_ID = "journeymap";

    private DomainsJourneyMapIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(JOURNEYMAP_MOD_ID);
    }
}
