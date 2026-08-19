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

/**
 * Left-click claims, right-click unclaims, Shift+left-click claims+chunkloads (or just turns
 * chunkload on if already claimed), and Shift+right-click turns chunkload off without unclaiming —
 * for a chunk on Xaero's World Map or JourneyMap's fullscreen map. This is the exact same button
 * scheme {@code SolarisClaimMapScreen#performClaimAction}/{@code #performUnclaimAction} already
 * use, extended to whichever of those two other mods' maps a player might have open instead (not
 * gated to only work when Domains itself opened the screen — active any time either mod's map is
 * the current screen, including via that mod's own keybind).
 * <p>
 * Unlike the first version of this integration, plain left/right clicks are <b>not</b> gated
 * behind Shift — matching Solaris's own scheme was a deliberate, explicit choice to prioritize a
 * single consistent muscle-memory control scheme across every map, accepting the risk that a
 * plain click here could someday collide with some plain-click behavior of the host mod's own
 * (e.g. waypoint placement) that wasn't hit during testing.
 * <p>
 * Deliberately never references {@code xaero.map.gui.GuiMap}/{@code
 * journeymap.client.ui.fullscreen.Fullscreen} (or anything else optional-mod-specific) in this
 * class's own method signatures or casts — this class is an always-loaded {@code
 * @Mod.EventBusSubscriber}, registered regardless of which map mods are installed, so it must stay
 * safe to load/verify on a system with neither mod present. {@link XaeroClaimClickBridge}/{@link
 * JourneyMapClaimClickBridge} hold the real per-mod references and are only ever touched behind
 * their respective {@code isAvailable()} gate below — same split {@code DomainHudOverlay} already
 * uses for every other optional integration.
 * <p>
 * Uses {@code ScreenEvent.MouseButtonPressed.Pre} (a stable, documented Forge API, cancelable so
 * the underlying mod's own click handling can be suppressed after we've acted) rather than a
 * Mixin {@code @Inject} into either mod's own mouse-click handling — injecting there would mean
 * correctly guessing SRG-vs-official method-name mapping for an inherited vanilla {@code Screen}
 * override in a closed-source jar with no way to compile-test against it live, a real fragility
 * risk this sidesteps entirely.
 */
@Mod.EventBusSubscriber(modid = PhoenixDomains.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClaimClickHandler {

    // Same broken-flag latch pattern as every other optional integration in this mod (see
    // DomainHudOverlay) — a mixin/API mismatch disables just this one integration for the
    // session, logged once, rather than retrying (and potentially re-throwing) on every click.
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

    /**
     * Draws a small legend in the top-left corner whenever Xaero's World Map or JourneyMap's
     * fullscreen map is open — without it there's no on-screen indication that these controls
     * exist at all, since (unlike {@code SolarisClaimMapScreen}) this integration doesn't own the
     * screen and can't add its own permanent sidebar. Uses {@code ScreenEvent.Render.Post}, which
     * fires for every {@code Screen} instance regardless of which mod defines it (patched into
     * Forge's own screen-render call site, not into any particular {@code Screen} subclass), so
     * this needs no per-mod hook of its own — just the same {@code isTargetScreen} check the
     * bridges already expose.
     */
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
