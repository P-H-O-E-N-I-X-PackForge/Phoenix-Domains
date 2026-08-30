package net.phoenixvine.domains.ownership;

import net.phoenixvine.guilds.GuildAPI;

import java.awt.Color;
import java.util.UUID;

public final class DomainOwnership {

    private DomainOwnership() {}

    public static UUID tokenFor(UUID playerUUID) {
        return GuildAPI.getGuildIdOrPlayerFallback(playerUUID);
    }

    public static boolean isMemberOrSelf(UUID playerUUID, UUID token) {
        return GuildAPI.isPlayerInGuildOrIs(playerUUID, token);
    }

    public static String displayName(UUID token) {
        return GuildAPI.getDisplayName(token);
    }

    public static int colorFor(UUID token) {
        if (token == null) return 0xFFAAAAAA;
        float hue = (token.hashCode() & 0xFFFF) / 65536f;
        return 0xFF000000 | (Color.HSBtoRGB(hue, 0.55f, 0.9f) & 0xFFFFFF);
    }
}
