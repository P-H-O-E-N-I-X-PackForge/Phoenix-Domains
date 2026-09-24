package net.phoenixvine.domains.ownership;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

interface OwnershipBackend {

    UUID tokenFor(UUID playerUUID);

    boolean isMemberOrSelf(UUID playerUUID, UUID token);

    String displayName(UUID token);

    boolean isAllied(ServerPlayer player, UUID ownerToken);

    boolean hasManageRank(UUID playerUUID);

    Optional<UUID> tokenByName(MinecraftServer server, String name);
}
