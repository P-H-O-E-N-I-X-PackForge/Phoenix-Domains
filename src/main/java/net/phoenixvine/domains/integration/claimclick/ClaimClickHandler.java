package net.phoenixvine.domains.integration.claimclick;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.integration.journeymap.DomainsJourneyMapIntegration;
import net.phoenixvine.domains.integration.journeymap.JourneyMapClaimClickBridge;
import net.phoenixvine.domains.integration.xaero.DomainsXaeroWorldMapIntegration;
import net.phoenixvine.domains.integration.xaero.XaeroClaimClickBridge;

import java.util.List;

@Mod.EventBusSubscriber(modid = PhoenixDomains.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClaimClickHandler {

    private static boolean xaeroClaimClickBroken = false;
    private static boolean journeyMapClaimClickBroken = false;

    private ClaimClickHandler() {}

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        int button = event.getButton();
        if (button != 0 && button != 1) return;

        Screen screen = event.getScreen();
        boolean chunkloadToggle = Screen.hasShiftDown();

        if (!xaeroClaimClickBroken && DomainsXaeroWorldMapIntegration.isAvailable()) {
            try {
                if (XaeroClaimClickBridge.tryHandle(screen, button, chunkloadToggle)) {
                    event.setCanceled(true);
                    return;
                }
            } catch (Throwable t) {
                xaeroClaimClickBroken = true;
                PhoenixDomains.LOGGER.error("Xaero's World Map is present but click-to-claim failed —" +
                        " disabled for the rest of this session.", t);
            }
        }

        if (!journeyMapClaimClickBroken && DomainsJourneyMapIntegration.isAvailable()) {
            try {
                if (JourneyMapClaimClickBridge.tryHandle(screen, event.getMouseX(), event.getMouseY(), button,
                        chunkloadToggle)) {
                    event.setCanceled(true);
                }
            } catch (Throwable t) {
                journeyMapClaimClickBroken = true;
                PhoenixDomains.LOGGER.error("JourneyMap is present but click-to-claim failed — disabled for" +
                        " the rest of this session.", t);
            }
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        boolean onXaero = !xaeroClaimClickBroken && DomainsXaeroWorldMapIntegration.isAvailable() &&
                XaeroClaimClickBridge.isTargetScreen(screen);
        boolean onJourneyMap = !journeyMapClaimClickBroken && DomainsJourneyMapIntegration.isAvailable() &&
                JourneyMapClaimClickBridge.isTargetScreen(screen);
        if (!onXaero && !onJourneyMap) return;

        GuiGraphics g = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        List<Component> lines = List.of(
                Component.translatable("domains.map.legend_claim"),
                Component.translatable("domains.map.legend_chunkload"),
                Component.translatable("domains.map.legend_stats", ClientDomainCache.usedClaimBlocks,
                        ClientDomainCache.availableClaimBlocks, ClientDomainCache.usedChunkloadBlocks,
                        ClientDomainCache.availableChunkloadBlocks));

        int lineH = font.lineHeight + 2;
        int maxWidth = 0;
        for (Component line : lines) maxWidth = Math.max(maxWidth, font.width(line));

        int x = 6;
        int y = 6;
        int padding = 4;
        g.fill(x, y, x + maxWidth + padding * 2, y + lines.size() * lineH + padding * 2, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x + padding, y + padding + i * lineH, 0xFFFFFFFF);
        }
    }
}
