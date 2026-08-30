package net.phoenixvine.domains.integration.xaero;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.phoenixvine.domains.client.ClientDomainCache;

import xaero.common.minimap.highlight.ChunkHighlighter;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

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
                                          int chunkZ, int width) {}
}
