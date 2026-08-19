package net.phoenixvine.domains.integration.xaero;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.integration.claimclick.ClaimClickActions;

import xaero.map.gui.GuiMap;

import java.lang.reflect.Field;

/**
 * Click-to-claim for Xaero's World Map — unlike {@link DomainsXaeroWorldMapIntegration} (a pure
 * {@code ModList} gatekeeper with zero real {@code xaero.*} references), this class contains the
 * actual integration and must only ever be touched after {@link
 * DomainsXaeroWorldMapIntegration#isAvailable()} is true. {@code ClaimClickHandler} (the always
 * -loaded Forge event listener) never references {@link GuiMap} directly itself — it only calls
 * {@link #tryHandle(Screen, int)}, whose signature is plain {@code Screen}/{@code int}, so merely
 * loading/verifying {@code ClaimClickHandler} can never force-resolve Xaero's types on a system
 * that doesn't have the mod.
 * <p>
 * Reads {@link GuiMap}'s private, continuously-updated "world block under the cursor" fields
 * ({@code mouseBlockPosX}/{@code mouseBlockPosZ}/{@code mouseBlockDim}) via plain reflection, not
 * a Mixin {@code @Accessor} — a {@code @Mixin} interface targeting {@link GuiMap} was tried first
 * (both a static and an instance-method accessor form for the equivalent JourneyMap field), but
 * neither actually got applied at runtime for this project's Mixin setup (confirmed live via a
 * {@code ClassCastException}: the target class never implemented the accessor interface, with no
 * corresponding Mixin-side error logged to explain why). Reflection sidesteps that mystery
 * entirely and is a mature, well-understood mechanism for exactly this "read a known-shape private
 * field, best-effort" need — Forge's ModLauncher already permits cross-mod reflection access in
 * practice. Field lookup happens once in a static initializer; a missing/renamed field on some
 * future Xaero version just leaves {@link #FIELDS_RESOLVED} false, disabling this integration
 * cleanly rather than crashing.
 */
public final class XaeroClaimClickBridge {

    private static Field mouseBlockPosXField;
    private static Field mouseBlockPosZField;
    private static Field mouseBlockDimField;
    private static boolean fieldsResolved;

    static {
        try {
            mouseBlockPosXField = GuiMap.class.getDeclaredField("mouseBlockPosX");
            mouseBlockPosXField.setAccessible(true);
            mouseBlockPosZField = GuiMap.class.getDeclaredField("mouseBlockPosZ");
            mouseBlockPosZField.setAccessible(true);
            mouseBlockDimField = GuiMap.class.getDeclaredField("mouseBlockDim");
            mouseBlockDimField.setAccessible(true);
            fieldsResolved = true;
        } catch (ReflectiveOperationException e) {
            PhoenixDomains.LOGGER.error("Xaero's World Map click-to-claim: couldn't find the expected private" +
                    " fields on GuiMap (Xaero update changed its internals?) — disabled.", e);
            fieldsResolved = false;
        }
    }

    private XaeroClaimClickBridge() {}

    /** Whether {@code screen} is Xaero's World Map — used by {@code ClaimClickHandler}'s legend overlay. */
    public static boolean isTargetScreen(Screen screen) {
        return screen instanceof GuiMap;
    }

    /**
     * Returns {@code false} (does nothing) if {@code screen} isn't Xaero's World Map, the click
     * isn't a left/right button, the expected private fields couldn't be found at class-load time,
     * or the mouse isn't currently over real map content (no dimension resolved). Otherwise claims
     * (button 0) or unclaims (button 1) the hovered chunk — or toggles chunkload instead when
     * {@code chunkloadToggle} is set (Shift held, see {@code ClaimClickHandler}) — and returns
     * {@code true}, so the caller knows to cancel the underlying click.
     */
    public static boolean tryHandle(Screen screen, int button, boolean chunkloadToggle)
                                                                                        throws ReflectiveOperationException {
        if (!(screen instanceof GuiMap guiMap) || (button != 0 && button != 1) || !fieldsResolved) return false;

        int chunkX = ((int) mouseBlockPosXField.get(guiMap)) >> 4;
        int chunkZ = ((int) mouseBlockPosZField.get(guiMap)) >> 4;
        @SuppressWarnings("unchecked")
        ResourceKey<Level> dim = (ResourceKey<Level>) mouseBlockDimField.get(guiMap);
        if (dim == null) return false;

        ClaimClickActions.perform(chunkX, chunkZ, button == 0, chunkloadToggle);
        return true;
    }
}
