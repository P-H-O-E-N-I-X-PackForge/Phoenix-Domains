package net.phoenixvine.domains.integration.journeymap;

import net.minecraft.client.gui.screens.Screen;

/** Interim stand-in; see {@link DomainsJourneyMapIntegration}. */
public final class JourneyMapClaimClickBridge {

    private JourneyMapClaimClickBridge() {}

    public static boolean tryHandle(Screen screen, double mouseX, double mouseY, int button, boolean chunkloadToggle) {
        return false;
    }

    public static boolean isTargetScreen(Screen screen) {
        return false;
    }
}
