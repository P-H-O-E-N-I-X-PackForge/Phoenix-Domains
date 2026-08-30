package net.phoenixvine.domains.integration.xaero;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;

import xaero.map.highlight.ChunkHighlighter;

import java.util.List;
import java.util.Objects;

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

    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return Objects.hash(regionX, regionZ, ClientDomainCache.version);
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> tooltips, ResourceKey<Level> dimension, int blockX,
                                                 int blockZ, int width) {}

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
