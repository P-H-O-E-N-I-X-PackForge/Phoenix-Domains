package net.phoenixvine.domains.integration.journeymap;

import net.minecraftforge.fml.ModList;

/**
 * Optional integration with JourneyMap. {@code journeymap} is declared as a non-mandatory
 * {@code mods.toml} dependency, so this class must contain ONLY the availability check — zero
 * imports or references to anything in {@code journeymap.*}, same reasoning as
 * {@code DomainsChroniclesIntegration}: calling any static method on a class forces the JVM to
 * load/verify that class's whole bytecode, so if the real JourneyMap-referencing logic lived
 * here, merely calling {@link #isAvailable()} would already try to resolve JourneyMap's types on
 * a system that doesn't have it.
 * <p>
 * Unlike {@code DomainsSolarisIntegration}, Domains never has to actively call into
 * {@link DomainJourneyMapPlugin} — JourneyMap discovers it itself via classpath scanning for the
 * {@code @ClientPlugin} annotation, and only ever instantiates it if JourneyMap is present. This
 * gatekeeper class exists so the rest of Domains (logging, HUD messaging) can still ask "is
 * JourneyMap here" without needing to import anything JourneyMap-specific.
 */
public final class DomainsJourneyMapIntegration {

    public static final String JOURNEYMAP_MOD_ID = "journeymap";

    private DomainsJourneyMapIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(JOURNEYMAP_MOD_ID);
    }
}
