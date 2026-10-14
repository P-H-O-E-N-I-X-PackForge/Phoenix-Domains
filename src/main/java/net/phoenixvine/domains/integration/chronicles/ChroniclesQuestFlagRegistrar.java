package net.phoenixvine.domains.integration.chronicles;

import net.phoenixvine.chronicles.common.flag.PhoenixQuestFlags;

public final class ChroniclesQuestFlagRegistrar {

    private ChroniclesQuestFlagRegistrar() {}

    public static void register() {
        PhoenixQuestFlags.registerProvider(new DomainQuestFlagProvider());
    }
}
