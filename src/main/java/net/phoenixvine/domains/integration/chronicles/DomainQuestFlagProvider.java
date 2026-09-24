package net.phoenixvine.domains.integration.chronicles;

import net.minecraft.server.MinecraftServer;

import net.phoenixvine.chronicles.common.flag.FlagExpression;
import net.phoenixvine.chronicles.common.flag.QuestFlagProvider;
import net.phoenixvine.domains.data.DomainManager;

import javax.annotation.Nullable;

public class DomainQuestFlagProvider implements QuestFlagProvider {

    @Override
    public String prefix() {
        return "domain";
    }

    @Override
    public boolean evaluate(String expression, @Nullable MinecraftServer server) {
        if (server == null) return true;

        FlagExpression expr = FlagExpression.parse(expression);
        int claimCount = DomainManager.get(server.overworld()).getClaimCount();

        return switch (expr.key) {
            case "any_claims" -> expr.test(String.valueOf(claimCount > 0));
            case "claims" -> expr.test(String.valueOf(claimCount));
            default -> false;
        };
    }
}
