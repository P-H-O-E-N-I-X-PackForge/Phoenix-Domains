package net.phoenixvine.domains.ownership;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.awt.Color;
import java.util.Optional;
import java.util.UUID;

public final class DomainOwnership {

    private static OwnershipBackend backend;

    private DomainOwnership() {}

    private static OwnershipBackend backend() {
        if (backend == null) {
            if (ModList.get().isLoaded("phoenix_guilds")) {
                backend = new GuildsBackend();
            } else if (ModList.get().isLoaded("ftbteams")) {
                backend = new FtbTeamsBackend();
            } else {
                throw new IllegalStateException(
                        "Phoenix Domains requires either Phoenix Guilds or FTB Teams to be installed " +
                                "for group ownership of claims.");
            }
        }
        return backend;
    }

    public static UUID tokenFor(UUID playerUUID) {
        return backend().tokenFor(playerUUID);
    }

    public static boolean isMemberOrSelf(UUID playerUUID, UUID token) {
        return backend().isMemberOrSelf(playerUUID, token);
    }

    public static String displayName(UUID token) {
        return backend().displayName(token);
    }

    public static boolean isAllied(ServerPlayer player, UUID ownerToken) {
        return backend().isAllied(player, ownerToken);
    }

    public static boolean hasManageRank(UUID playerUUID) {
        return backend().hasManageRank(playerUUID);
    }

    public static Optional<UUID> tokenByName(MinecraftServer server, String name) {
        return backend().tokenByName(server, name);
    }

    public static int colorFor(UUID token) {
        if (token == null) return 0xFFAAAAAA;
        float hue = (token.hashCode() & 0xFFFF) / 65536f;
        return 0xFF000000 | (Color.HSBtoRGB(hue, 0.55f, 0.9f) & 0xFFFFFF);
    }
}
