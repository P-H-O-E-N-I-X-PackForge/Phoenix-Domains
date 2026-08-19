package net.phoenixvine.domains.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.api.DomainAPI;
import net.phoenixvine.domains.data.ChunkKey;
import net.phoenixvine.domains.data.Claim;
import net.phoenixvine.domains.data.DomainManager;
import net.phoenixvine.domains.ownership.DomainOwnership;

import java.util.ArrayList;
import java.util.List;

public class DomainNetwork {

    private static final String PROTOCOL = "1";
    public static SimpleChannel CHANNEL;

    private static int id = 0;

    public static void init() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(PhoenixDomains.MOD_ID, "main"),
                () -> PROTOCOL,
                PROTOCOL::equals,
                PROTOCOL::equals);

        CHANNEL.registerMessage(id++,
                C2SDomainActionPacket.class,
                C2SDomainActionPacket::encode,
                C2SDomainActionPacket::new,
                C2SDomainActionPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                S2CDomainSyncPacket.class,
                S2CDomainSyncPacket::encode,
                S2CDomainSyncPacket::new,
                S2CDomainSyncPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** Gathers claims within {@code radius} chunks of the player and pushes them, plus the player's power pools. */
    public static void sendSync(ServerPlayer player, int radius) {
        BlockPos playerPos = player.blockPosition();
        sendSyncAt(player, playerPos.getX() >> 4, playerPos.getZ() >> 4, radius);
    }

    /**
     * Same as {@link #sendSync(ServerPlayer, int)}, but centered on an explicit chunk rather than
     * the player's own position. {@code ClientDomainCache#update} merges each incoming sync
     * by-region (clearing then repopulating only the {@code centerX/centerZ/radius} square this
     * packet actually covers, leaving everything else the client already knew about untouched) —
     * see its own doc for why a plain full-replace used to silently erase any claim knowledge
     * outside whatever the most recent sync happened to cover. That fix alone doesn't help if the
     * relevant chunk is never queried at all, though: the periodic player-centered sync ({@link
     * #sendSync(ServerPlayer, int)}) only ever looks near the player's own physical position, so a
     * chunk claimed/unclaimed far from there (e.g. clicked on a zoomed-out Xaero/JourneyMap view)
     * would never be included in any sync, ever — {@code C2SDomainActionPacket} calls this
     * directly, centered on the specific chunk just acted on, alongside the normal player-centered
     * sync, so that chunk's true state reaches the client regardless of the player's own location.
     */
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
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private static S2CDomainSyncPacket.ClaimEntry toEntry(Claim claim) {
        String name = DomainOwnership.displayName(claim.getOwner());
        int color = DomainOwnership.colorFor(claim.getOwner());
        return new S2CDomainSyncPacket.ClaimEntry(claim.getKey().x(), claim.getKey().z(), name, color,
                claim.isChunkloaded());
    }
}
