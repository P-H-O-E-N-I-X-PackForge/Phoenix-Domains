package net.phoenixvine.domains.integration.chronicles;

public final class ChroniclesQuestFlagRegistrar {

    private ChroniclesQuestFlagRegistrar() {}

    public static void register() {
        net.phoenixvine.chronicles.common.flag.PhoenixQuestFlags.registerProvider(new DomainQuestFlagProvider());
    }
}
