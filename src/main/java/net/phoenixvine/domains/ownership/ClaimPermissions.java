package net.phoenixvine.domains.ownership;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.domains.data.Claim;
import net.phoenixvine.domains.data.ClaimFlag;
import net.phoenixvine.guilds.GuildAPI;
import net.phoenixvine.guilds.data.Guild;
import net.phoenixvine.guilds.data.GuildManager;
import net.phoenixvine.guilds.data.GuildRank;

import java.util.Optional;
import java.util.UUID;

public final class ClaimPermissions {

    private ClaimPermissions() {}

    public static boolean isTrusted(ServerPlayer player, Claim claim) {
        return DomainOwnership.isMemberOrSelf(player.getUUID(), claim.getOwner());
    }

    public static boolean isAllied(ServerPlayer player, Claim claim) {
        GuildManager mgr = GuildManager.get(player.getServer().overworld());
        Optional<Guild> ownerGuild = mgr.getGuildById(claim.getOwner());
        if (ownerGuild.isEmpty()) return false;
        Optional<Guild> playerGuild = mgr.getGuildFor(player.getUUID());
        return playerGuild.isPresent() && ownerGuild.get().isAlly(playerGuild.get().getId());
    }

    public static boolean canBuild(ServerPlayer player, Claim claim) {
        if (isTrusted(player, claim)) return true;
        return isAllied(player, claim) && claim.getFlag(ClaimFlag.ALLY_BUILD);
    }

    public static boolean canInteract(ServerPlayer player, Claim claim) {
        if (isTrusted(player, claim)) return true;
        return isAllied(player, claim) && claim.getFlag(ClaimFlag.ALLY_INTERACT);
    }

    public static boolean canOpenContainer(ServerPlayer player, Claim claim) {
        if (isTrusted(player, claim)) return true;
        return isAllied(player, claim) && claim.getFlag(ClaimFlag.ALLY_CONTAINERS);
    }

    public static boolean canPvp(Claim claim) {
        return claim.getFlag(ClaimFlag.PVP);
    }

    public static boolean canManage(ServerPlayer player, UUID ownerToken) {
        UUID myToken = DomainOwnership.tokenFor(player.getUUID());
        if (!myToken.equals(ownerToken)) return false;
        if (player.getUUID().equals(ownerToken)) return true;
        return GuildAPI.hasRank(player.getUUID(), GuildRank.OFFICER);
    }
}
