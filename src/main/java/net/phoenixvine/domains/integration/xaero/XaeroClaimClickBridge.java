package net.phoenixvine.domains.integration.xaero;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.integration.claimclick.ClaimClickActions;

import xaero.map.gui.GuiMap;

import java.lang.reflect.Field;

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

    public static boolean isTargetScreen(Screen screen) {
        return screen instanceof GuiMap;
    }

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
