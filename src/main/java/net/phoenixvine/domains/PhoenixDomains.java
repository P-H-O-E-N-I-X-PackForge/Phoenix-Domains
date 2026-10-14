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

    public PhoenixDomains(IEventBus modEventBus, ModContainer container) {
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
