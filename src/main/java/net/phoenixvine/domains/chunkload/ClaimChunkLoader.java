package net.phoenixvine.domains.chunkload;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.data.ChunkKey;
import net.phoenixvine.domains.data.Claim;
import net.phoenixvine.domains.data.DomainManager;

import java.util.Optional;

public final class ClaimChunkLoader {

    private ClaimChunkLoader() {}

    public static void register() {
        ForgeChunkManager.setForcedChunkLoadingCallback(PhoenixDomains.MOD_ID, ClaimChunkLoader::validateTickets);
    }

    private static void validateTickets(ServerLevel level, ForgeChunkManager.TicketHelper helper) {
        DomainManager manager = DomainManager.get(level.getServer().overworld());
        ResourceLocation dim = level.dimension().location();

        helper.getBlockTickets().forEach((owner, pair) -> {
            for (long packed : pair.getFirst()) validateChunk(helper, manager, dim, owner, packed, true);
            for (long packed : pair.getSecond()) validateChunk(helper, manager, dim, owner, packed, false);
        });
    }

    private static void validateChunk(ForgeChunkManager.TicketHelper helper, DomainManager manager,
                                      ResourceLocation dim, BlockPos owner, long packed, boolean ticking) {
        ChunkPos pos = new ChunkPos(packed);
        ChunkKey key = new ChunkKey(dim, pos.x, pos.z);
        Optional<Claim> claim = manager.getClaim(key);
        if (claim.isEmpty() || !claim.get().isChunkloaded()) {
            helper.removeTicket(owner, packed, ticking);
        }
    }

    public static void setChunkForced(ServerLevel level, ChunkKey key, boolean forced) {
        BlockPos owner = new BlockPos(key.x(), 0, key.z());
        ForgeChunkManager.forceChunk(level, PhoenixDomains.MOD_ID, owner, key.x(), key.z(), forced, true);
    }
}
