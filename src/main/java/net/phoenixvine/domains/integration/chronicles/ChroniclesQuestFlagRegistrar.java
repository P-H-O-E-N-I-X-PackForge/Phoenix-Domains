package net.phoenixvine.domains.integration.chronicles;

public final class ChroniclesQuestFlagRegistrar {

    private ChroniclesQuestFlagRegistrar() {}

    public static void register() {
        net.phoenixvine.chronicles.flag.PhoenixQuestFlags.registerProvider(new DomainQuestFlagProvider());
    }
}
