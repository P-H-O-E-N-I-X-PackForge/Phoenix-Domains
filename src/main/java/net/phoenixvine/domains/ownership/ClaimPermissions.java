package net.phoenixvine.domains.ownership;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.domains.data.Claim;
import net.phoenixvine.domains.data.ClaimFlag;

import java.util.UUID;

public final class ClaimPermissions {

    private ClaimPermissions() {}

    public static boolean isTrusted(ServerPlayer player, Claim claim) {
        return DomainOwnership.isMemberOrSelf(player.getUUID(), claim.getOwner());
    }

    public static boolean isAllied(ServerPlayer player, Claim claim) {
        return DomainOwnership.isAllied(player, claim.getOwner());
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
        return DomainOwnership.hasManageRank(player.getUUID());
    }
}
