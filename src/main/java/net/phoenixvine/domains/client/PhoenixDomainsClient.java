package net.phoenixvine.domains.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.wiki.client.suite.SuiteHudBar;
import net.phoenixvine.wiki.theme.PhoenixTheme;

public class PhoenixDomainsClient {

    public static void init(IEventBus modEventBus) {
        modEventBus.register(DomainKeybinds.class);
        modEventBus.addListener(PhoenixDomainsClient::clientSetup);
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        // Lets the suite's shared/per-mod theme toggle (see PhoenixTheme#setSharedMode) tell this
        // mod's own code apart from every other Phoenix mod's when they call the no-arg theme
        // accessors -- see PhoenixTheme#resolveCallerModId.
        PhoenixTheme.registerMod("net.phoenixvine.domains", PhoenixDomains.MOD_ID);

        SuiteHudBar.register(PhoenixDomains.MOD_ID, SuiteHudBar.PRIORITY_DOMAINS,
                ResourceLocation.fromNamespaceAndPath(PhoenixDomains.MOD_ID, "textures/gui/suite_bar_icon.png"),
                Component.literal("§fOpen Claim Map"),
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    Screen current = mc.screen;
                    mc.setScreen(null);
                    DomainHudOverlay.openClaimMap(current);
                });
    }
}
