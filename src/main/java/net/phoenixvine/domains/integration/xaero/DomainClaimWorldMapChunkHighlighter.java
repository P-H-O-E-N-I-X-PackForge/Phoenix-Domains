package net.phoenixvine.domains.integration.xaero;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;

import xaero.map.highlight.ChunkHighlighter;

import java.util.List;
import java.util.Objects;

/**
 * The Xaero World Map (fullscreen map) counterpart to {@link DomainClaimChunkHighlighter} — same
 * fill/border color logic via {@link DomainClaimHighlightColors}, but a different base class
 * (world map and minimap are separate mods/jars with their own, near-identical but distinctly
 * -packaged {@code ChunkHighlighter}/{@code HighlighterRegistry} types — confirmed against both
 * real jars, not assumed) and a tooltip-method shape that returns a {@link Component} directly
 * instead of writing into an {@code InfoDisplayCompiler}. Registered via {@code
 * DomainXaeroWorldMapSessionMixin}.
 */
public class DomainClaimWorldMapChunkHighlighter extends ChunkHighlighter {

    public DomainClaimWorldMapChunkHighlighter() {
        super(false);
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return !ClientDomainCache.claims.isEmpty();
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return DomainClaimHighlightColors.compute(dimension, chunkX, chunkZ, resultStore);
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return DomainClaimHighlightColors.isHighlit(dimension, chunkX, chunkZ);
    }

    /**
     * Coarser than GTCEu's own per-chunk accumulation (which iterates all 1024 chunks in the
     * region) — just folds in {@code ClientDomainCache.version}, which changes on every claim
     * sync. Invalidates every visible highlighted region on any single claim change rather than
     * only the actually-affected one, but claim changes are an infrequent user action, not a hot
     * path, so the simpler always-correct version is worth the minor over-invalidation.
     */
    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return Objects.hash(regionX, regionZ, ClientDomainCache.version);
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> tooltips, ResourceKey<Level> dimension, int blockX,
                                                 int blockZ, int width) {
        // No block-level tooltip content for v1, matching the minimap side's own no-op.
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return ownerTooltip(dimension, chunkX, chunkZ);
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return ownerTooltip(dimension, chunkX, chunkZ);
    }

    private static Component ownerTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (!DomainClaimHighlightColors.isHighlit(dimension, chunkX, chunkZ)) return null;
        S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(chunkX, chunkZ);
        return entry == null ? null : Component.literal(entry.ownerName());
    }
}
