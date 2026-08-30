package net.phoenixvine.domains.integration.journeymap;

import net.phoenixvine.domains.PhoenixDomains;

import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.event.ClientEvent;

import java.util.EnumSet;

@ClientPlugin
public class DomainJourneyMapPlugin implements IClientPlugin {

    private static IClientAPI api;
    private static final DomainClaimJourneyMapOverlay overlay = new DomainClaimJourneyMapOverlay();

    @Override
    public void initialize(IClientAPI jmClientApi) {
        api = jmClientApi;

        api.subscribe(PhoenixDomains.MOD_ID, EnumSet.of(ClientEvent.Type.DISPLAY_UPDATE));
    }

    @Override
    public String getModId() {
        return PhoenixDomains.MOD_ID;
    }

    @Override
    public void onEvent(ClientEvent event) {
        if (event.type == ClientEvent.Type.DISPLAY_UPDATE) sync();
    }

    public static void sync() {
        if (api != null) overlay.sync(api);
    }
}
