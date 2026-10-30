package net.phoenixvine.domains.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.wiki.api.gate.SuiteGates;

/**
 * What a player may do with Domains, as features of the shared gate API ({@link SuiteGates}): another mod binds them to
 * research or anything else, e.g. {@code SuiteGates.bind(DomainGates.CHUNKLOADING, "*", "conflux:node/foo")}. A feature
 * that nothing is bound to is allowed, so a pack that binds nothing sees no change. The scope is the dimension id.
 */
public final class DomainGates {

    public static final String CLAIMING = "domains:" + DomainAPI.FEATURE_CLAIMING;
    public static final String CHUNKLOADING = "domains:" + DomainAPI.FEATURE_CHUNKLOADING;
    public static final String CLAIM_FLAGS = "domains:" + DomainAPI.FEATURE_CLAIM_FLAGS;

    private DomainGates() {}

    public static boolean allowed(ServerPlayer player, String feature, ResourceLocation dimension) {
        return SuiteGates.allowed(player, feature, dimension.toString());
    }
}
