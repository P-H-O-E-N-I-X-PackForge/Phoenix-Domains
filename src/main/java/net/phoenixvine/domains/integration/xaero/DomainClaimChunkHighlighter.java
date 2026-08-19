package net.phoenixvine.domains.integration.xaero;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.phoenixvine.domains.client.ClientDomainCache;

import xaero.common.minimap.highlight.ChunkHighlighter;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

/**
 * Real claim-region highlighting on Xaero's Minimap — a genuine, public (if lightly documented)
 * Xaero extension point confirmed against the actual jar and against GTCEu's own production
 * {@code FluidChunkHighlighter}, not a mixin hack: fill + edge-only border colors per chunk, so
 * a claimed territory reads as one outlined region instead of Solaris's flat per-chunk tint.
 * Registered via {@code DomainXaeroHighlighterRegistryMixin} — {@code HighlighterRegistry
 * .register(AbstractHighlighter)} is itself an ordinary public method, the mixin exists only
 * because there's no public hook yet to obtain the registry instance at the right moment (see
 * that mixin's own doc).
 */
public class DomainClaimChunkHighlighter extends ChunkHighlighter {

    public DomainClaimChunkHighlighter() {
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

    @Override
    public void addChunkHighlightTooltips(InfoDisplayCompiler compiler, ResourceKey<Level> dimension, int chunkX,
                                          int chunkZ, int width) {
        // No tooltip content for v1 — the highlighted region + Xaero's own chunk-coordinate
        // readout already answers "is this claimed"; per-chunk owner labeling can be a follow-up.
    }
}
