package net.phoenixvine.domains.integration.journeymap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.integration.claimclick.ClaimClickActions;

import journeymap.client.render.map.MapRenderer;
import journeymap.client.ui.fullscreen.Fullscreen;

import java.awt.geom.Point2D;
import java.lang.reflect.Field;

public final class JourneyMapClaimClickBridge {

    private static Field mapRendererField;
    private static boolean fieldResolved;

    static {
        try {
            mapRendererField = Fullscreen.class.getDeclaredField("mapRenderer");
            mapRendererField.setAccessible(true);
            fieldResolved = true;
        } catch (ReflectiveOperationException e) {
            PhoenixDomains.LOGGER.error("JourneyMap click-to-claim: couldn't find the expected private field on" +
                    " Fullscreen (JourneyMap update changed its internals?) — disabled.", e);
            fieldResolved = false;
        }
    }

    private JourneyMapClaimClickBridge() {}

    public static boolean isTargetScreen(Screen screen) {
        return screen instanceof Fullscreen;
    }

    public static boolean tryHandle(Screen screen, double mouseX, double mouseY, int button, boolean chunkloadToggle)
                                                                                                                      throws ReflectiveOperationException {
        if (!(screen instanceof Fullscreen fullscreen) || (button != 0 && button != 1) || !fieldResolved) {
            return false;
        }

        MapRenderer grid = (MapRenderer) mapRendererField.get(null);
        if (grid == null) return false;

        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        BlockPos pos = grid.getBlockAtPixel(new Point2D.Double(mouseX * guiScale, mouseY * guiScale));
        if (pos == null) return false;

        ClaimClickActions.perform(pos.getX() >> 4, pos.getZ() >> 4, button == 0, chunkloadToggle);
        return true;
    }
}
