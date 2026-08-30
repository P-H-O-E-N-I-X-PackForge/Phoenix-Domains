package net.phoenixvine.domains.integration.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;

final class DomainClaimHighlightColors {

    private static final int CHUNKLOADED_RGB = 0xFFD700;
    private static final int FILL_OPACITY_PERCENT = 40;
    private static final int BORDER_OPACITY_PERCENT = 70;

    private DomainClaimHighlightColors() {}

    static int[] compute(ResourceKey<Level> dimension, int chunkX, int chunkZ, int[] resultStore) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(dimension)) return null;

        S2CDomainSyncPacket.ClaimEntry self = ClientDomainCache.entryAt(chunkX, chunkZ);
        if (self == null) return null;

        int fillColor = toHighlighterColor(rgbOf(self), FILL_OPACITY_PERCENT);
        int borderColor = toHighlighterColor(rgbOf(self), BORDER_OPACITY_PERCENT);

        resultStore[0] = fillColor;
        resultStore[1] = edgeColor(self, ClientDomainCache.entryAt(chunkX, chunkZ - 1), fillColor, borderColor);
        resultStore[2] = edgeColor(self, ClientDomainCache.entryAt(chunkX + 1, chunkZ), fillColor, borderColor);
        resultStore[3] = edgeColor(self, ClientDomainCache.entryAt(chunkX, chunkZ + 1), fillColor, borderColor);
        resultStore[4] = edgeColor(self, ClientDomainCache.entryAt(chunkX - 1, chunkZ), fillColor, borderColor);
        return resultStore;
    }

    static boolean isHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        var level = Minecraft.getInstance().level;
        return level != null && level.dimension().equals(dimension) &&
                ClientDomainCache.entryAt(chunkX, chunkZ) != null;
    }

    private static int edgeColor(S2CDomainSyncPacket.ClaimEntry self, S2CDomainSyncPacket.ClaimEntry neighbor,
                                 int fillColor, int borderColor) {
        boolean sameOwner = neighbor != null && neighbor.ownerName().equals(self.ownerName());
        return sameOwner ? fillColor : borderColor;
    }

    private static int rgbOf(S2CDomainSyncPacket.ClaimEntry entry) {
        return entry.chunkloaded() ? CHUNKLOADED_RGB : (entry.color() & 0xFFFFFF);
    }

    private static int toHighlighterColor(int rgb, int opacityPercent) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        int alpha = 255 * opacityPercent / 100;
        return r << 24 | g << 16 | b << 8 | alpha;
    }
}
