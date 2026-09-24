package net.phoenixvine.domains.ownership;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;

import java.util.Optional;
import java.util.UUID;

final class FtbTeamsBackend implements OwnershipBackend {

    private static Optional<Team> teamFor(UUID playerUUID) {
        return FTBTeamsAPI.api().getManager().getTeamForPlayerID(playerUUID);
    }

    @Override
    public UUID tokenFor(UUID playerUUID) {
        return teamFor(playerUUID).map(Team::getId).orElse(playerUUID);
    }

    @Override
    public boolean isMemberOrSelf(UUID playerUUID, UUID token) {
        if (playerUUID.equals(token)) return true;
        return teamFor(playerUUID).map(team -> team.getId().equals(token)).orElse(false);
    }

    @Override
    public String displayName(UUID token) {
        return FTBTeamsAPI.api().getManager().getTeamByID(token)
                .map(team -> team.getName().getString())
                .orElse(token.toString().substring(0, 8));
    }

    @Override
    public boolean isAllied(ServerPlayer player, UUID ownerToken) {
        // FTB Teams has no team-to-team alliance concept, so ally-gated permissions never apply here.
        return false;
    }

    @Override
    public boolean hasManageRank(UUID playerUUID) {
        return teamFor(playerUUID).map(team -> team.getRankForPlayer(playerUUID).isOfficerOrBetter()).orElse(false);
    }

    @Override
    public Optional<UUID> tokenByName(MinecraftServer server, String name) {
        return FTBTeamsAPI.api().getManager().getTeamByName(name).map(Team::getId);
    }
}
