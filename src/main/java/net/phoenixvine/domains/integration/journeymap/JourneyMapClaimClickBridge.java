package net.phoenixvine.domains.integration.journeymap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.integration.claimclick.ClaimClickActions;

import journeymap.client.render.map.GridRenderer;
import journeymap.client.ui.fullscreen.Fullscreen;

import java.awt.geom.Point2D;
import java.lang.reflect.Field;

/**
 * Click-to-claim for JourneyMap's fullscreen map — unlike {@link DomainsJourneyMapIntegration} (a
 * pure {@code ModList} gatekeeper with zero real {@code journeymap.*} references), this class
 * contains the actual integration and must only ever be touched after {@link
 * DomainsJourneyMapIntegration#isAvailable()} is true. {@code ClaimClickHandler} (the always
 * -loaded Forge event listener) never references {@link Fullscreen}/{@link GridRenderer} directly
 * itself — it only calls {@link #tryHandle(Screen, double, double, int)}, whose signature is
 * plain {@code Screen}/{@code double}/{@code int}, so merely loading/verifying {@code
 * ClaimClickHandler} can never force-resolve JourneyMap's types on a system that doesn't have it.
 * <p>
 * Reads {@link Fullscreen}'s private static {@code gridRenderer} field via plain reflection, not
 * a Mixin {@code @Accessor} — both a static and an instance-method accessor form were tried first
 * and neither actually got applied at runtime (confirmed live via a {@code ClassCastException}:
 * {@link Fullscreen} never implemented the accessor interface, with no corresponding Mixin-side
 * error logged to explain why). Reflection sidesteps that mystery entirely. Field lookup happens
 * once in a static initializer; a renamed field on some future JourneyMap version just leaves
 * {@link #fieldResolved} false, disabling this integration cleanly rather than crashing.
 */
public final class JourneyMapClaimClickBridge {

    private static Field gridRendererField;
    private static boolean fieldResolved;

    static {
        try {
            gridRendererField = Fullscreen.class.getDeclaredField("gridRenderer");
            gridRendererField.setAccessible(true);
            fieldResolved = true;
        } catch (ReflectiveOperationException e) {
            PhoenixDomains.LOGGER.error("JourneyMap click-to-claim: couldn't find the expected private field on" +
                    " Fullscreen (JourneyMap update changed its internals?) — disabled.", e);
            fieldResolved = false;
        }
    }

    private JourneyMapClaimClickBridge() {}

    /** Whether {@code screen} is JourneyMap's fullscreen map — used by {@code ClaimClickHandler}'s legend overlay. */
    public static boolean isTargetScreen(Screen screen) {
        return screen instanceof Fullscreen;
    }

    /**
     * Returns {@code false} (does nothing) if {@code screen} isn't JourneyMap's fullscreen map,
     * the click isn't a left/right button, the expected private field couldn't be found at
     * class-load time, or {@link GridRenderer#getBlockAtPixel} couldn't resolve a world position
     * for this screen point. Otherwise claims (button 0) or unclaims (button 1) the hovered chunk
     * — or toggles chunkload instead when {@code chunkloadToggle} is set (Shift held, see {@code
     * ClaimClickHandler}) — and returns {@code true}, so the caller knows to cancel the underlying
     * click.
     */
    public static boolean tryHandle(Screen screen, double mouseX, double mouseY, int button, boolean chunkloadToggle)
                                                                                                                      throws ReflectiveOperationException {
        if (!(screen instanceof Fullscreen fullscreen) || (button != 0 && button != 1) || !fieldResolved) {
            return false;
        }

        GridRenderer grid = (GridRenderer) gridRendererField.get(fullscreen);
        if (grid == null) return false;

        // GridRenderer#getBlockAtPixel (verified against the decompiled jar) computes its center
        // offset from its own lastWidth/lastHeight fields against the raw point — those track the
        // map viewport in physical framebuffer pixels, not the logical GUI-scaled coordinates
        // Forge's ScreenEvent gives us, which is what actually produced "resolves nowhere near
        // where clicked" (worse the higher the GUI scale). Scaling up here to match.
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        BlockPos pos = grid.getBlockAtPixel(new Point2D.Double(mouseX * guiScale, mouseY * guiScale));
        if (pos == null) return false;

        ClaimClickActions.perform(pos.getX() >> 4, pos.getZ() >> 4, button == 0, chunkloadToggle);
        return true;
    }
}
