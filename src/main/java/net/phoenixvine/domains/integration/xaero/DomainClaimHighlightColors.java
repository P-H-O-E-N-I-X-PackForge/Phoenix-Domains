package net.phoenixvine.domains.integration.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;

/**
 * Shared per-chunk color computation for both Xaero highlighter subclasses ({@code
 * DomainClaimChunkHighlighter} for the minimap, {@code DomainClaimWorldMapChunkHighlighter} for
 * the fullscreen world map) — the two sides' {@code ChunkHighlighter} base classes live in
 * different packages ({@code xaero.common.minimap.highlight} vs {@code xaero.map.highlight}) and
 * differ in their tooltip method shapes, but the actual "what color is this chunk" logic is
 * identical, so it's factored out here rather than duplicated. Deliberately has no Xaero imports
 * at all — callers pass in their own {@code resultStore} array to fill (the same array both real
 * base classes already expose as a protected field) so this class stays a plain int[]-in,
 * int[]-out helper.
 * <p>
 * Real fill-vs-border distinction: same color as the flat tint everywhere else in this mod
 * family (gold if chunkloaded, else the claim's own {@code entry.color()}) for the interior,
 * but an edge only gets the (more opaque) border color where the neighboring chunk has a
 * <i>different</i> owner or isn't claimed at all — a same-owner neighbor blends smoothly into a
 * single filled region instead of every chunk getting a fully-boxed outline.
 */
final class DomainClaimHighlightColors {

    private static final int CHUNKLOADED_RGB = 0xFFD700;
    private static final int FILL_OPACITY_PERCENT = 40;
    private static final int BORDER_OPACITY_PERCENT = 70;

    private DomainClaimHighlightColors() {}

    /**
     * Fills {@code resultStore} (must be length 5: center, top, right, bottom, left, matching
     * {@code AbstractHighlighter}'s own layout) and returns it, or returns {@code null} if this
     * chunk isn't claimed / isn't in the currently-rendered dimension — the same "nothing to
     * highlight here" signal both real {@code ChunkHighlighter} base classes expect.
     */
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

    /**
     * Domains' colors are conventionally alpha-high ({@code 0xFFRRGGBB}, see {@code
     * DomainOwnership.colorFor}) — Xaero's highlighter API wants alpha-low RGBA ints instead
     * ({@code R<<24 | G<<16 | B<<8 | A}), confirmed against GTCEu's own production
     * {@code FluidChunkHighlighter}, which performs the exact same channel rearrangement before
     * handing a color to this same API.
     */
    private static int toHighlighterColor(int rgb, int opacityPercent) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        int alpha = 255 * opacityPercent / 100;
        return r << 24 | g << 16 | b << 8 | alpha;
    }
}
