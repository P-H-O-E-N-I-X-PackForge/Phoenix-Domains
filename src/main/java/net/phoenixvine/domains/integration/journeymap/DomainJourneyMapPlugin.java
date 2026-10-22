package net.phoenixvine.domains.integration.journeymap;

import net.phoenixvine.domains.PhoenixDomains;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;

@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public class DomainJourneyMapPlugin implements IClientPlugin {

    private static IClientAPI api;
    private static final DomainClaimJourneyMapOverlay overlay = new DomainClaimJourneyMapOverlay();

    @Override
    public void initialize(IClientAPI jmClientApi) {
        api = jmClientApi;

        ClientEventRegistry.DISPLAY_UPDATE_EVENT.subscribe(PhoenixDomains.MOD_ID, event -> sync());
    }

    @Override
    public String getModId() {
        return PhoenixDomains.MOD_ID;
    }

    public static void sync() {
        if (api != null) overlay.sync(api);
    }
}
