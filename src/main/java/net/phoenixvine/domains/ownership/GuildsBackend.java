package net.phoenixvine.domains.ownership;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.guilds.GuildAPI;
import net.phoenixvine.guilds.data.Guild;
import net.phoenixvine.guilds.data.GuildManager;
import net.phoenixvine.guilds.data.GuildRank;

import java.util.Optional;
import java.util.UUID;

final class GuildsBackend implements OwnershipBackend {

    @Override
    public UUID tokenFor(UUID playerUUID) {
        return GuildAPI.getGuildIdOrPlayerFallback(playerUUID);
    }

    @Override
    public boolean isMemberOrSelf(UUID playerUUID, UUID token) {
        return GuildAPI.isPlayerInGuildOrIs(playerUUID, token);
    }

    @Override
    public String displayName(UUID token) {
        return GuildAPI.getDisplayName(token);
    }

    @Override
    public boolean isAllied(ServerPlayer player, UUID ownerToken) {
        GuildManager mgr = GuildManager.get(player.getServer().overworld());
        Optional<Guild> ownerGuild = mgr.getGuildById(ownerToken);
        if (ownerGuild.isEmpty()) return false;
        Optional<Guild> playerGuild = mgr.getGuildFor(player.getUUID());
        return playerGuild.isPresent() && ownerGuild.get().isAlly(playerGuild.get().getId());
    }

    @Override
    public boolean hasManageRank(UUID playerUUID) {
        return GuildAPI.hasRank(playerUUID, GuildRank.OFFICER);
    }

    @Override
    public Optional<UUID> tokenByName(MinecraftServer server, String name) {
        return GuildManager.get(server.overworld()).getGuildByName(name).map(Guild::getId);
    }
}
