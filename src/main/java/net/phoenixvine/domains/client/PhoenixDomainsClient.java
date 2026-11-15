package net.phoenixvine.domains.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.phoenixvine.domains.DomainsLang;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.wiki.client.suite.SuiteHudBar;
import net.phoenixvine.wiki.theme.PhoenixTheme;

public class PhoenixDomainsClient {

    public static void init(IEventBus modEventBus) {
        modEventBus.register(DomainKeybinds.class);
        modEventBus.addListener(PhoenixDomainsClient::clientSetup);
    }

    private static void clientSetup(final FMLClientSetupEvent event) {

        PhoenixTheme.registerMod("net.phoenixvine.domains", PhoenixDomains.MOD_ID);

        SuiteHudBar.register(PhoenixDomains.MOD_ID, SuiteHudBar.PRIORITY_DOMAINS,
                ResourceLocation.fromNamespaceAndPath(PhoenixDomains.MOD_ID, "textures/gui/suite_bar_icon.png"),
                DomainsLang.tr("domains.phoenix_domains_client.open_claim_map"),
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    Screen current = mc.screen;
                    mc.setScreen(null);
                    DomainHudOverlay.openClaimMap(current);
                });
    }
}
