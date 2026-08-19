package net.phoenixvine.domains.integration.journeymap;

import net.phoenixvine.domains.PhoenixDomains;

import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.event.ClientEvent;

import java.util.EnumSet;

/**
 * JourneyMap discovers this class itself via classpath scanning for {@code @ClientPlugin} — it's
 * only ever instantiated (via a required no-arg constructor) if JourneyMap is actually present
 * and scanning, so unlike {@code DomainsSolarisIntegration}'s overlay Domains never has to call
 * into this class to register it. Real per-claim polygon logic lives in
 * {@link DomainClaimJourneyMapOverlay}; this class is just the JourneyMap-facing glue —
 * {@link #initialize} stashes the API handle, {@link #sync()} is the one thing the rest of
 * Domains calls (from {@code DomainHudOverlay.onClientTick}, guarded by
 * {@code DomainsJourneyMapIntegration.isAvailable()} first, same pattern as the Solaris overlay's
 * {@code requestRefresh()}) whenever {@code ClientDomainCache.version} changes.
 */
@ClientPlugin
public class DomainJourneyMapPlugin implements IClientPlugin {

    private static IClientAPI api;
    private static final DomainClaimJourneyMapOverlay overlay = new DomainClaimJourneyMapOverlay();

    @Override
    public void initialize(IClientAPI jmClientApi) {
        api = jmClientApi;
        // DISPLAY_UPDATE is JourneyMap's own signal to re-show everything for the dimension
        // indicated (e.g. after a map reload) — Domains' own claim changes still need the
        // separate sync() poll below, since JourneyMap has no way to know about those on its own.
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

    /** Call only after {@code DomainsJourneyMapIntegration.isAvailable()} has returned true. */
    public static void sync() {
        if (api != null) overlay.sync(api);
    }
}
