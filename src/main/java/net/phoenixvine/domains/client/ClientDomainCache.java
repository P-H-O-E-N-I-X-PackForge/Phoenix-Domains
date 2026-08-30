package net.phoenixvine.domains.client;

import net.phoenixvine.domains.network.S2CDomainSyncPacket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientDomainCache {

    private ClientDomainCache() {}

    public static List<S2CDomainSyncPacket.ClaimEntry> claims = List.of();
    public static long availableClaimBlocks = 0;
    public static long usedClaimBlocks = 0;
    public static long availableChunkloadBlocks = 0;
    public static long usedChunkloadBlocks = 0;

    public static int version = 0;

    private static Map<Long, S2CDomainSyncPacket.ClaimEntry> byChunk = Map.of();

    public static void update(List<S2CDomainSyncPacket.ClaimEntry> claimList, int centerX, int centerZ, int radius,
                              long availClaim, long usedClaim, long availChunkload, long usedChunkload) {
        availableClaimBlocks = availClaim;
        usedClaimBlocks = usedClaim;
        availableChunkloadBlocks = availChunkload;
        usedChunkloadBlocks = usedChunkload;

        Map<Long, S2CDomainSyncPacket.ClaimEntry> merged = new HashMap<>(byChunk);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                merged.remove(packKey(centerX + dx, centerZ + dz));
            }
        }
        for (S2CDomainSyncPacket.ClaimEntry c : claimList) merged.put(packKey(c.x(), c.z()), c);
        byChunk = merged;
        claims = List.copyOf(merged.values());

        version++;
    }

    public static S2CDomainSyncPacket.ClaimEntry entryAt(int chunkX, int chunkZ) {
        return byChunk.get(packKey(chunkX, chunkZ));
    }

    private static long packKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
