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

    public PhoenixDomains() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

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

            // Guarded by isAvailable(), but a stale/mismatched build of an optional
            // dependency can still report itself present in ModList while lacking a
            // class we need (NoClassDefFoundError) — catch broadly so a bad optional
            // jar degrades this one integration instead of crashing Domains entirely.
            // The actual registration (which touches Chronicles' classes) lives in a
            // SEPARATE class (ChroniclesQuestFlagRegistrar) from this isAvailable() check -
            // calling a static method on a class forces the JVM to load/verify that class's
            // entire bytecode, so keeping the two in the same file meant just evaluating this
            // guard would already try to resolve Chronicles' types on a server without it.
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

    // Re-applies config/phoenix_domains-server-overrides.toml (if present) on top of
    // whatever the per-world serverconfig produced, every time it (re)loads — see
    // DomainsConfigOverrides for why this can't be done via a straightforward
    // ConfigValue#set(...) call. Fires for every mod's configs on this bus, so
    // DomainsConfigOverrides itself filters to DomainsConfig.SPEC.
    private void onConfigLoad(final ModConfigEvent.Loading event) {
        DomainsConfigOverrides.onLoad(event);
    }

    private void onConfigReload(final ModConfigEvent.Reloading event) {
        DomainsConfigOverrides.onLoad(event);
    }
}
