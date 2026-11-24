package net.phoenixvine.domains;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.phoenixvine.domains.chunkload.ClaimChunkLoader;
import net.phoenixvine.domains.client.PhoenixDomainsClient;
import net.phoenixvine.domains.config.DomainsClientConfig;
import net.phoenixvine.domains.config.DomainsConfig;
import net.phoenixvine.domains.config.DomainsConfigOverrides;
import net.phoenixvine.domains.integration.chronicles.ChroniclesQuestFlagRegistrar;
import net.phoenixvine.domains.integration.chronicles.DomainsChroniclesIntegration;
import net.phoenixvine.domains.network.DomainNetwork;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PhoenixDomains.MOD_ID)
public class PhoenixDomains {

    public static final String MOD_ID = "phoenix_domains";
    public static final Logger LOGGER = LogManager.getLogger();

    /** Domains shows claims on a map, so a client needs at least one of these. Any one of them is enough. */
    public static final java.util.List<String> MAP_MODS = java.util.List.of("solaris", "journeymap", "xaerominimap",
            "xaeroworldmap");

    private static boolean hasMapMod() {
        net.minecraftforge.fml.ModList mods = net.minecraftforge.fml.ModList.get();
        for (String id : MAP_MODS) if (mods.isLoaded(id)) return true;
        return false;
    }

    public PhoenixDomains() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // The map mods are client mods (JourneyMap and Xaero's), so a dedicated server is not asked for one.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == Dist.CLIENT && !hasMapMod()) {
            throw new IllegalStateException(
                    "Phoenix Domains needs a map mod to show claims on. Install at least one of: " +
                            "Solaris, JourneyMap, Xaero's Minimap or Xaero's World Map (any one is enough).");
        }

        DomainsConfig.register();
        DomainsClientConfig.register();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PhoenixDomainsClient.init(modEventBus));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DomainNetwork.init();
            ClaimChunkLoader.register();

            if (DomainsChroniclesIntegration.isAvailable()) {
                try {
                    ChroniclesQuestFlagRegistrar.register();
                    LOGGER.info("Phoenix Chronicles detected — registered domain: quest flags.");
                } catch (Throwable t) {
                    LOGGER.error("Phoenix Chronicles is present but its integration failed to initialize" +
                            " — domain: quest flags will be unavailable.", t);
                }
            }

            LOGGER.info("Phoenix Domains initializing...");
        });
    }

    private void onConfigLoad(final ModConfigEvent.Loading event) {
        DomainsConfigOverrides.onLoad(event);
    }

    private void onConfigReload(final ModConfigEvent.Reloading event) {
        DomainsConfigOverrides.onLoad(event);
    }
}
