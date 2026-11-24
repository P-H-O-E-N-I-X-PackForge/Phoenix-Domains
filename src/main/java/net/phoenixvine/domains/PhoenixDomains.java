package net.phoenixvine.domains;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
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
        net.neoforged.fml.ModList mods = net.neoforged.fml.ModList.get();
        for (String id : MAP_MODS) if (mods.isLoaded(id)) return true;
        return false;
    }

    public PhoenixDomains(IEventBus modEventBus, ModContainer container) {
        // The map mods are client mods (JourneyMap and Xaero's), so a dedicated server is not asked for one.
        if (FMLEnvironment.dist.isClient() && !hasMapMod()) {
            throw new IllegalStateException("Phoenix Domains needs a map mod to show claims on. Install at least one of: "
                    + "Solaris, JourneyMap, Xaero's Minimap or Xaero's World Map (any one is enough).");
        }
        DomainsConfig.register(container);
        DomainsClientConfig.register(container);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);

        modEventBus.addListener(DomainNetwork::register);
        ClaimChunkLoader.register(modEventBus);

        if (FMLEnvironment.dist.isClient()) PhoenixDomainsClient.init(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
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
