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

@Mod.EventBusSubscriber(modid = PhoenixDomains.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DomainHudOverlay {

    private static final int SYNC_INTERVAL_TICKS = 40;
    private static final int SYNC_RADIUS = 8;

    private static int tickCounter = 0;
    private static int lastSeenCacheVersion = -1;

    private static boolean solarisBroken = false;
    private static boolean journeyMapBroken = false;
    private static boolean xaeroMinimapBroken = false;

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

    public static void openClaimMap() {
        openClaimMap(null);
    }

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
