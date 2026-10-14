package net.phoenixvine.domains.chunkload;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.data.ChunkKey;
import net.phoenixvine.domains.data.Claim;
import net.phoenixvine.domains.data.DomainManager;

import java.util.Optional;

public final class ClaimChunkLoader {

    private static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(PhoenixDomains.MOD_ID, "claims"), ClaimChunkLoader::validateTickets);

    private ClaimChunkLoader() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterTicketControllersEvent event) -> event.register(CONTROLLER));
    }

    private static void validateTickets(ServerLevel level, TicketHelper helper) {
        DomainManager manager = DomainManager.get(level.getServer().overworld());
        ResourceLocation dim = level.dimension().location();

        helper.getBlockTickets().forEach((owner, tickets) -> {
            for (long packed : tickets.ticking()) validateChunk(helper, manager, dim, owner, packed, true);
            for (long packed : tickets.nonTicking()) validateChunk(helper, manager, dim, owner, packed, false);
        });
    }

    private static void validateChunk(TicketHelper helper, DomainManager manager,
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
        CONTROLLER.forceChunk(level, owner, key.x(), key.z(), forced, true);
    }
}
