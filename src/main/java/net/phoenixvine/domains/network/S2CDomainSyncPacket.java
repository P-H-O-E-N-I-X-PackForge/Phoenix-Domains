package net.phoenixvine.domains.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.domains.client.ClientDomainCache;

import java.util.ArrayList;
import java.util.List;

public class S2CDomainSyncPacket implements CustomPacketPayload {

    public static final Type<S2CDomainSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_domains", "sync"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, S2CDomainSyncPacket> STREAM_CODEC = StreamCodec
            .ofMember(S2CDomainSyncPacket::encode, S2CDomainSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record ClaimEntry(int x, int z, String ownerName, int color, boolean chunkloaded) {}

    private final List<ClaimEntry> claims;

    private final int centerX;
    private final int centerZ;
    private final int radius;
    private final long availableClaimBlocks;
    private final long usedClaimBlocks;
    private final long availableChunkloadBlocks;
    private final long usedChunkloadBlocks;

    public S2CDomainSyncPacket(List<ClaimEntry> claims, int centerX, int centerZ, int radius,
                               long availableClaimBlocks, long usedClaimBlocks, long availableChunkloadBlocks,
                               long usedChunkloadBlocks) {
        this.claims = claims;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.availableClaimBlocks = availableClaimBlocks;
        this.usedClaimBlocks = usedClaimBlocks;
        this.availableChunkloadBlocks = availableChunkloadBlocks;
        this.usedChunkloadBlocks = usedChunkloadBlocks;
    }

    public S2CDomainSyncPacket(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ClaimEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new ClaimEntry(buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(DomainNetworkLimits.OWNER_NAME_MAX), buf.readInt(), buf.readBoolean()));
        }
        this.claims = list;
        this.centerX = buf.readVarInt();
        this.centerZ = buf.readVarInt();
        this.radius = buf.readVarInt();
        this.availableClaimBlocks = buf.readLong();
        this.usedClaimBlocks = buf.readLong();
        this.availableChunkloadBlocks = buf.readLong();
        this.usedChunkloadBlocks = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(claims.size());
        for (ClaimEntry c : claims) {
            buf.writeVarInt(c.x());
            buf.writeVarInt(c.z());
            buf.writeUtf(c.ownerName(), DomainNetworkLimits.OWNER_NAME_MAX);
            buf.writeInt(c.color());
            buf.writeBoolean(c.chunkloaded());
        }
        buf.writeVarInt(centerX);
        buf.writeVarInt(centerZ);
        buf.writeVarInt(radius);
        buf.writeLong(availableClaimBlocks);
        buf.writeLong(usedClaimBlocks);
        buf.writeLong(availableChunkloadBlocks);
        buf.writeLong(usedChunkloadBlocks);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> applyOnClient(this));
    }

    private static void applyOnClient(S2CDomainSyncPacket pkt) {
        if (Minecraft.getInstance().player == null) return;
        ClientDomainCache.update(pkt.claims, pkt.centerX, pkt.centerZ, pkt.radius, pkt.availableClaimBlocks,
                pkt.usedClaimBlocks, pkt.availableChunkloadBlocks, pkt.usedChunkloadBlocks);
    }
}
