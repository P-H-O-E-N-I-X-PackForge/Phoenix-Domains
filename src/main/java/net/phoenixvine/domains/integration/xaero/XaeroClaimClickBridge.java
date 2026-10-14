package net.phoenixvine.domains.integration.xaero;

import net.minecraft.client.gui.screens.Screen;

/** Interim stand-in; see {@link net.phoenixvine.domains.integration.xaero.DomainsXaeroMinimapIntegration}. */
public final class XaeroClaimClickBridge {

    private XaeroClaimClickBridge() {}

    public static boolean tryHandle(Screen screen, int button, boolean chunkloadToggle) {
        return false;
    }

    public static boolean isTargetScreen(Screen screen) {
        return false;
    }
}
