package net.phoenixvine.domains.client;

import net.phoenixvine.domains.network.S2CDomainSyncPacket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side mirror of nearby claim data and the local player's power pools, updated by {@code S2CDomainSyncPacket}.
 */
public final class ClientDomainCache {

    private ClientDomainCache() {}

    public static List<S2CDomainSyncPacket.ClaimEntry> claims = List.of();
    public static long availableClaimBlocks = 0;
    public static long usedClaimBlocks = 0;
    public static long availableChunkloadBlocks = 0;
    public static long usedChunkloadBlocks = 0;

    /** Bumped every {@link #update}, so consumers (e.g. the Solaris overlay refresh) can detect changes cheaply. */
    public static int version = 0;

    // entryAt() is called once per visible chunk by the Solaris claim-tint overlay every time
    // its map texture rebuilds — with map radii in the dozens of chunks, a linear scan there
    // multiplied out fast enough to visibly stutter the render thread, most noticeably right
    // when clicking to claim/chunkload (that's exactly when a rebuild fires). Indexed by
    // packed chunk coords instead, so lookups are O(1) regardless of how many claims exist.
    private static Map<Long, S2CDomainSyncPacket.ClaimEntry> byChunk = Map.of();

    /**
     * Merges a sync by-region: a sync packet only ever describes claims within the square
     * {@code centerX/centerZ +/- radius} it was gathered for on the server, so this clears just
     * that square from the cache before repopulating it from {@code claimList} — any chunk in the
     * square absent from the list is now known-unclaimed, but chunks outside the square (from
     * earlier syncs covering other areas) are left untouched. A plain full-replace here used to
     * silently erase every claim the client knew about outside whatever the latest sync happened
     * to cover, which is why far-away claim/unclaim actions (e.g. from a zoomed-out map click)
     * never visibly took effect.
     */
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

    /** Looks up the cached entry for a chunk, or null if it's unclaimed (or not yet synced). */
    public static S2CDomainSyncPacket.ClaimEntry entryAt(int chunkX, int chunkZ) {
        return byChunk.get(packKey(chunkX, chunkZ));
    }

    private static long packKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
