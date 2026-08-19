package net.phoenixvine.domains.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.client.map.ClaimMapScreen;
import net.phoenixvine.domains.config.DomainsClientConfig;
import net.phoenixvine.domains.integration.journeymap.DomainJourneyMapPlugin;
import net.phoenixvine.domains.integration.journeymap.DomainsJourneyMapIntegration;
import net.phoenixvine.domains.integration.journeymap.JourneyMapEmbeddedClaimScreen;
import net.phoenixvine.domains.integration.solaris.DomainsSolarisIntegration;
import net.phoenixvine.domains.integration.xaero.DomainXaeroWaypointSync;
import net.phoenixvine.domains.integration.xaero.DomainsXaeroMinimapIntegration;
import net.phoenixvine.domains.integration.xaero.DomainsXaeroWorldMapIntegration;
import net.phoenixvine.domains.integration.xaero.XaeroEmbeddedClaimScreen;
import net.phoenixvine.domains.network.C2SDomainActionPacket;
import net.phoenixvine.domains.network.DomainNetwork;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;

/**
 * Rough v1 HUD: a single centered top-of-screen line naming whoever owns the chunk the player is
 * standing in (or "Wilderness"). Also drives the periodic sync request that keeps {@link
 * ClientDomainCache} populated for both this and the claim map screen(s), and pokes the
 * JourneyMap/Xaero Minimap integrations' claim-overlay sync whenever {@link ClientDomainCache}
 * changes — neither of those mods knows on its own when Domains' claim data changes, so this is
 * the only trigger for either (see {@code DomainJourneyMapPlugin}/{@code DomainXaeroWaypointSync}).
 * <p>
 * The map keybind always opens a screen Domains itself owns — never the real, live {@code GuiMap}
 * /{@code Fullscreen} screen those mods' own keybinds open, so our own claim-click handling can
 * never collide with their panning/waypoint clicks. Priority: Solaris's real-terrain claim screen
 * ({@code SolarisClaimMapScreen}, using its own excellent map API) if Solaris is present; else
 * Xaero's World Map's real terrain via {@link XaeroEmbeddedClaimScreen} (which puppeteers a real,
 * un-activated {@code GuiMap} instance purely for its visuals); else JourneyMap's real terrain via
 * {@link JourneyMapEmbeddedClaimScreen} (which owns a private {@code GridRenderer} instance, a
 * genuinely standalone public API); else the vanilla-only {@link ClaimMapScreen} if none of those
 * are present or all fail. Claim borders/waypoints still render on Xaero's/JourneyMap's own real
 * maps whenever a player opens either through that mod's own keybind, via the overlay-sync
 * integrations below — those are unaffected by this keybind's own choice of screen. (Xaero's
 * World Map and JourneyMap's fullscreen map also still support click-to-claim directly, via
 * {@code ClaimClickHandler}, for players who get there through that mod's own keybind instead of
 * this one.)
 */
@Mod.EventBusSubscriber(modid = PhoenixDomains.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DomainHudOverlay {

    private static final int SYNC_INTERVAL_TICKS = 40; // 2 real-time seconds
    private static final int SYNC_RADIUS = 8;

    private static int tickCounter = 0;
    private static int lastSeenCacheVersion = -1;

    // Set once if a call into the respective integration throws, so a stale/mismatched optional
    // jar (ModList says present, but a class we need is actually missing/incompatible) logs once
    // and stops retrying that one integration, instead of retrying — and potentially
    // re-throwing — on every single client tick. Independent per integration so a broken
    // JourneyMap jar, say, doesn't also disable the (unrelated) Solaris integration.
    private static boolean solarisBroken = false;
    private static boolean journeyMapBroken = false;
    private static boolean xaeroMinimapBroken = false;
    // Separate from journeyMapBroken/xaeroMinimapBroken above — those cover the overlay-sync
    // integrations (claim borders/waypoints drawn on an already-open real map); these cover the
    // map keybind's own embedded-real-terrain screens, a different code path (real GuiMap/
    // GridRenderer puppeteering) that can fail independently of whether the overlay sync itself
    // is still working.
    private static boolean xaeroEmbedBroken = false;
    private static boolean journeyMapEmbedBroken = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean cacheChanged = ClientDomainCache.version != lastSeenCacheVersion;
        lastSeenCacheVersion = ClientDomainCache.version;

        boolean solarisAvailable = !solarisBroken && DomainsSolarisIntegration.isAvailable();
        if (solarisAvailable) {
            try {
                DomainsSolarisIntegration.init();
                if (cacheChanged) DomainsSolarisIntegration.requestRefresh();
            } catch (Throwable t) {
                solarisBroken = true;
                solarisAvailable = false;
                PhoenixDomains.LOGGER.error("Solaris is present but its integration failed — falling back to" +
                        " Domains' own claim map for the rest of this session.", t);
            }
        }

        if (!journeyMapBroken && DomainsJourneyMapIntegration.isAvailable() && cacheChanged) {
            try {
                DomainJourneyMapPlugin.sync();
            } catch (Throwable t) {
                journeyMapBroken = true;
                PhoenixDomains.LOGGER.error("JourneyMap is present but its integration failed — claim borders" +
                        " won't show there for the rest of this session.", t);
            }
        }

        if (!xaeroMinimapBroken && DomainsXaeroMinimapIntegration.isAvailable() && cacheChanged) {
            try {
                DomainXaeroWaypointSync.sync();
            } catch (Throwable t) {
                xaeroMinimapBroken = true;
                PhoenixDomains.LOGGER.error("Xaero's Minimap is present but its integration failed — claim" +
                        " owner waypoints won't show there for the rest of this session.", t);
            }
        }

        while (DomainKeybinds.TOGGLE_HUD.consumeClick()) {
            DomainsClientConfig.SHOW_HUD.set(!DomainsClientConfig.SHOW_HUD.get());
            DomainsClientConfig.SHOW_HUD.save();
        }

        while (DomainKeybinds.OPEN_MAP.consumeClick()) {
            openClaimMap();
        }

        if (++tickCounter < SYNC_INTERVAL_TICKS) return;
        tickCounter = 0;
        DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.requestSync(SYNC_RADIUS));
    }

    /**
     * Opens Domains' own claim map, in the same priority order described on the class doc: Solaris's
     * real-terrain screen if present and visible, else Xaero's World Map embedded screen, else
     * JourneyMap's embedded screen, else the vanilla-only {@link ClaimMapScreen} fallback. This is
     * the single shared implementation of that priority chain — both the {@code OPEN_MAP} keybind
     * above and the suite HUD bar button (registered with {@code SuiteHudBar} in
     * {@code PhoenixDomainsClient}) call this exact method rather than duplicating the chain.
     * No-ops if some other screen is already open or the player isn't currently in a world.
     * <p>
     * Equivalent to calling {@link #openClaimMap(Screen)} with {@code null} — i.e. there's nothing
     * to return to when the map closes. Used by the {@code OPEN_MAP} keybind, which only ever fires
     * from plain gameplay with no screen open in the first place.
     */
    public static void openClaimMap() {
        openClaimMap(null);
    }

    /**
     * Same as {@link #openClaimMap()}, but threads {@code returnTo} through to whichever claim map
     * screen ends up opening, so that screen hands the player back to {@code returnTo} on close
     * instead of dropping to the world. The suite HUD bar button's registered click callback uses
     * this overload, passing the screen that was open (e.g. the inventory screen) when the button
     * was clicked.
     */
    public static void openClaimMap(Screen returnTo) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.level == null || mc.player == null) return;

        boolean solarisAvailable = !solarisBroken && DomainsSolarisIntegration.isAvailable();
        if (solarisAvailable) {
            try {
                DomainsSolarisIntegration.init();
            } catch (Throwable t) {
                solarisBroken = true;
                solarisAvailable = false;
                PhoenixDomains.LOGGER.error("Solaris is present but its integration failed — falling back to" +
                        " Domains' own claim map for the rest of this session.", t);
            }
        }

        if (solarisAvailable) {
            boolean visible;
            try {
                visible = DomainsSolarisIntegration.claimMapState(mc.level.dimension().location())
                        .atLeast(DomainsSolarisIntegration.FeatureState.VISIBLE);
            } catch (Throwable t) {
                solarisBroken = true;
                PhoenixDomains.LOGGER.error(
                        "Failed to open the Solaris-backed claim map — falling back to Domains'" +
                                " vanilla-only claim map.",
                        t);
                visible = true;
            }
            if (!visible) {
                mc.player.displayClientMessage(
                        Component.translatable("domains.map.not_available"), true);
                return;
            }
        }

        Screen mapScreen = null;
        if (solarisAvailable) {
            try {
                mapScreen = DomainsSolarisIntegration.openClaimMapScreen(returnTo);
            } catch (Throwable t) {
                solarisBroken = true;
                PhoenixDomains.LOGGER.error(
                        "Failed to open the Solaris-backed claim map — falling back to the next" +
                                " available claim map.",
                        t);
            }
        }

        if (mapScreen == null && !xaeroEmbedBroken && DomainsXaeroWorldMapIntegration.isAvailable()) {
            try {
                if (XaeroEmbeddedClaimScreen.tryOpen(returnTo)) return;
            } catch (Throwable t) {
                xaeroEmbedBroken = true;
                PhoenixDomains.LOGGER.error("Xaero's World Map is present but the embedded claim map" +
                        " failed — falling back to the next available claim map for the rest of this" +
                        " session.", t);
            }
        }

        if (mapScreen == null && !journeyMapEmbedBroken && DomainsJourneyMapIntegration.isAvailable()) {
            try {
                if (JourneyMapEmbeddedClaimScreen.tryOpen(returnTo)) return;
            } catch (Throwable t) {
                journeyMapEmbedBroken = true;
                PhoenixDomains.LOGGER.error("JourneyMap is present but the embedded claim map failed —" +
                        " falling back to the next available claim map for the rest of this session.", t);
            }
        }

        mc.setScreen(mapScreen != null ? mapScreen : new ClaimMapScreen(returnTo));
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (!DomainsClientConfig.SHOW_HUD.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int chunkX = mc.player.blockPosition().getX() >> 4;
        int chunkZ = mc.player.blockPosition().getZ() >> 4;
        S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(chunkX, chunkZ);

        Component text = entry == null ? Component.translatable("domains.hud.wilderness") :
                Component.translatable("domains.hud.owner", entry.ownerName());

        Font font = mc.font;
        int width = font.width(text);
        int screenW = mc.getWindow().getGuiScaledWidth();
        int x = (screenW - width) / 2;
        int y = 4;
        int color = entry == null ? 0xFFAAAAAA : entry.color();

        GuiGraphics graphics = event.getGuiGraphics();
        graphics.drawString(font, text, x + 1, y + 1, 0xFF000000, false);
        graphics.drawString(font, text, x, y, color, false);
    }
}
