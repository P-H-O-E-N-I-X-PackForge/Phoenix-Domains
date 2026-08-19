package net.phoenixvine.domains.data;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers DomainManager's in-memory claim/power bookkeeping directly - it only needs a live
 * ServerLevel for its static SavedData factory (get()), everything else (claim/unclaim/
 * chunkload/flag mutation and the query methods) operates on its own maps, so `new
 * DomainManager()` here exercises the real logic without any game bootstrap.
 */
class DomainManagerTest {

    private static ChunkKey key(int x, int z) {
        return new ChunkKey(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), x, z);
    }

    @Test
    void claimingAnUnclaimedChunkSucceedsAndTracksOwnership() {
        DomainManager mgr = new DomainManager();
        UUID owner = UUID.randomUUID();
        ChunkKey k = key(0, 0);

        assertTrue(mgr.claim(k, owner));

        assertTrue(mgr.isClaimed(k));
        assertEquals(owner, mgr.getClaim(k).orElseThrow().getOwner());
        assertTrue(mgr.getClaimsForOwner(owner).contains(k));
        assertEquals(1, mgr.getClaimCount());
    }

    @Test
    void claimingAnAlreadyClaimedChunkFails() {
        DomainManager mgr = new DomainManager();
        ChunkKey k = key(0, 0);
        mgr.claim(k, UUID.randomUUID());

        assertFalse(mgr.claim(k, UUID.randomUUID()));
        assertEquals(1, mgr.getClaimCount());
    }

    @Test
    void claimingIncrementsTheOwnersClaimsUsed() {
        DomainManager mgr = new DomainManager();
        UUID owner = UUID.randomUUID();

        mgr.claim(key(0, 0), owner);
        mgr.claim(key(0, 1), owner);

        assertEquals(2, mgr.getOrCreatePower(owner).getClaimsUsed());
    }

    @Test
    void unclaimingARemovedChunkFails() {
        DomainManager mgr = new DomainManager();

        assertFalse(mgr.unclaim(key(0, 0)));
    }

    @Test
    void unclaimingRemovesTheChunkAndDecrementsClaimsUsed() {
        DomainManager mgr = new DomainManager();
        UUID owner = UUID.randomUUID();
        ChunkKey k = key(0, 0);
        mgr.claim(k, owner);

        assertTrue(mgr.unclaim(k));

        assertFalse(mgr.isClaimed(k));
        assertFalse(mgr.getClaimsForOwner(owner).contains(k));
        assertEquals(0, mgr.getOrCreatePower(owner).getClaimsUsed());
    }

    @Test
    void unclaimingAChunkloadedChunkAlsoDecrementsChunkloadsUsed() {
        DomainManager mgr = new DomainManager();
        UUID owner = UUID.randomUUID();
        ChunkKey k = key(0, 0);
        mgr.claim(k, owner);
        mgr.setChunkloaded(k, true);
        assertEquals(1, mgr.getOrCreatePower(owner).getChunkloadsUsed());

        mgr.unclaim(k);

        assertEquals(0, mgr.getOrCreatePower(owner).getChunkloadsUsed(),
                "unclaiming a chunkloaded claim should refund its chunkload usage too");
    }

    @Test
    void setChunkloadedOnAnUnclaimedChunkFails() {
        DomainManager mgr = new DomainManager();

        assertFalse(mgr.setChunkloaded(key(0, 0), true));
    }

    @Test
    void setChunkloadedToItsCurrentValueIsANoOpAndReportsFailure() {
        DomainManager mgr = new DomainManager();
        UUID owner = UUID.randomUUID();
        ChunkKey k = key(0, 0);
        mgr.claim(k, owner);

        assertFalse(mgr.setChunkloaded(k, false), "chunk starts un-chunkloaded, setting to false again should no-op");
        assertEquals(0, mgr.getOrCreatePower(owner).getChunkloadsUsed());
    }

    @Test
    void chunkloadedCountForOwnerOnlyCountsChunkloadedClaims() {
        DomainManager mgr = new DomainManager();
        UUID owner = UUID.randomUUID();
        mgr.claim(key(0, 0), owner);
        mgr.claim(key(0, 1), owner);
        mgr.claim(key(0, 2), owner);
        mgr.setChunkloaded(key(0, 0), true);
        mgr.setChunkloaded(key(0, 1), true);

        assertEquals(2, mgr.getChunkloadedCountForOwner(owner));
    }

    @Test
    void setFlagOnAnUnclaimedChunkIsANoOp() {
        DomainManager mgr = new DomainManager();
        ChunkKey k = key(0, 0);

        mgr.setFlag(k, ClaimFlag.PVP, true);

        assertFalse(mgr.isClaimed(k), "setFlag on a nonexistent claim should not create one");
    }

    @Test
    void setFlagOnAClaimedChunkIsReflectedOnTheClaim() {
        DomainManager mgr = new DomainManager();
        ChunkKey k = key(0, 0);
        mgr.claim(k, UUID.randomUUID());

        mgr.setFlag(k, ClaimFlag.PVP, true);

        assertTrue(mgr.getClaim(k).orElseThrow().getFlag(ClaimFlag.PVP));
    }

    @Test
    void getOrCreatePowerReturnsTheSameInstanceOnRepeatedCalls() {
        DomainManager mgr = new DomainManager();
        UUID owner = UUID.randomUUID();

        ClaimPower first = mgr.getOrCreatePower(owner);
        first.grantClaimPower(5);

        assertEquals(5, mgr.getOrCreatePower(owner).getEarnedClaimBlocks(0),
                "getOrCreatePower should return the same pooled instance, not a fresh one each time");
    }

    @Test
    void getPowerIsEmptyUntilFirstTouchedByGetOrCreatePower() {
        DomainManager mgr = new DomainManager();
        UUID owner = UUID.randomUUID();

        assertTrue(mgr.getPower(owner).isEmpty());

        mgr.getOrCreatePower(owner);

        assertTrue(mgr.getPower(owner).isPresent());
    }
}
