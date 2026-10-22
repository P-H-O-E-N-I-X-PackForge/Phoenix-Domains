package net.phoenixvine.domains.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.phoenixvine.domains.api.DomainAPI;
import net.phoenixvine.domains.data.ChunkKey;
import net.phoenixvine.domains.data.Claim;
import net.phoenixvine.domains.data.DomainManager;
import net.phoenixvine.domains.ownership.DomainOwnership;

import java.util.ArrayList;
import java.util.List;

public class DomainNetwork {

    private static final String PROTOCOL = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToServer(C2SDomainActionPacket.TYPE, C2SDomainActionPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToClient(S2CDomainSyncPacket.TYPE, S2CDomainSyncPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
    }

    public static void sendSync(ServerPlayer player, int radius) {
        BlockPos playerPos = player.blockPosition();
        sendSyncAt(player, playerPos.getX() >> 4, playerPos.getZ() >> 4, radius);
    }

    public static void sendSyncAt(ServerPlayer player, int centerX, int centerZ, int radius) {
        int clamped = Math.max(0, Math.min(radius, 32));
        DomainManager manager = DomainManager.get(player.getServer().overworld());
        ResourceLocation dim = player.level().dimension().location();

        List<S2CDomainSyncPacket.ClaimEntry> entries = new ArrayList<>();
        for (int dx = -clamped; dx <= clamped; dx++) {
            for (int dz = -clamped; dz <= clamped; dz++) {
                ChunkKey key = new ChunkKey(dim, centerX + dx, centerZ + dz);
                manager.getClaim(key).ifPresent(claim -> entries.add(toEntry(claim)));
            }
        }

        var token = DomainOwnership.tokenFor(player.getUUID());
        long availClaim = DomainAPI.getAvailableClaimBlocks(player.getServer(), token);
        long availChunkload = DomainAPI.getAvailableChunkloadBlocks(player.getServer(), token);
        var power = manager.getOrCreatePower(token);

        S2CDomainSyncPacket packet = new S2CDomainSyncPacket(entries, centerX, centerZ, clamped, availClaim,
                power.getClaimsUsed(), availChunkload, power.getChunkloadsUsed());
        PacketDistributor.sendToPlayer(player, packet);
    }

    private static S2CDomainSyncPacket.ClaimEntry toEntry(Claim claim) {
        String name = DomainOwnership.displayName(claim.getOwner());
        int color = DomainOwnership.colorFor(claim.getOwner());
        return new S2CDomainSyncPacket.ClaimEntry(claim.getKey().x(), claim.getKey().z(), name, color,
                claim.isChunkloaded());
    }
}
