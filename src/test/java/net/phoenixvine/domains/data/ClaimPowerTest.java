package net.phoenixvine.domains.data;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimPowerTest {

    @Test
    void earnedClaimBlocksCombinesBaseAccruedAndGranted() {
        ClaimPower power = new ClaimPower();
        power.accrueClaimPower(10.0, 1000.0);
        power.grantClaimPower(5);

        assertEquals(115, power.getEarnedClaimBlocks(100));
    }

    @Test
    void accruedClaimPowerIsCappedAtAccrualCap() {
        ClaimPower power = new ClaimPower();
        power.accrueClaimPower(50.0, 30.0);

        assertEquals(30, power.getEarnedClaimBlocks(0));
    }

    @Test
    void accruedClaimPowerIsFlooredWhenCombinedWithBase() {
        ClaimPower power = new ClaimPower();
        power.accrueClaimPower(9.9, 1000.0);

        assertEquals(9, power.getEarnedClaimBlocks(0), "partial accrual should floor, not round");
    }

    @Test
    void grantedClaimPowerCannotGoNegative() {
        ClaimPower power = new ClaimPower();
        power.grantClaimPower(5);
        power.grantClaimPower(-20);

        assertEquals(0, power.getEarnedClaimBlocks(0), "grants should clamp at zero, never go negative");
    }

    @Test
    void availableClaimBlocksSubtractsUsage() {
        ClaimPower power = new ClaimPower();
        power.grantClaimPower(10);
        power.incrementClaimsUsed();
        power.incrementClaimsUsed();

        assertEquals(8, power.getAvailableClaimBlocks(0));
        assertEquals(2, power.getClaimsUsed());
    }

    @Test
    void availableClaimBlocksNeverGoesNegativeWhenUsageExceedsEarned() {
        ClaimPower power = new ClaimPower();
        power.incrementClaimsUsed();
        power.incrementClaimsUsed();

        assertEquals(0, power.getAvailableClaimBlocks(0));
    }

    @Test
    void decrementClaimsUsedClampsAtZero() {
        ClaimPower power = new ClaimPower();
        power.decrementClaimsUsed();
        power.decrementClaimsUsed();

        assertEquals(0, power.getClaimsUsed());
    }

    @Test
    void chunkloadBudgetIsIndependentOfClaimBudget() {
        ClaimPower power = new ClaimPower();
        power.grantClaimPower(10);
        power.incrementClaimsUsed();
        power.grantChunkloadPower(3);
        power.incrementChunkloadsUsed();

        assertEquals(9, power.getAvailableClaimBlocks(0));
        assertEquals(2, power.getAvailableChunkloadBlocks(0));
    }

    @Test
    void serializeDeserializeRoundTripsAllFields() {
        ClaimPower original = new ClaimPower();
        original.accrueClaimPower(4.5, 1000.0);
        original.grantClaimPower(7);
        original.incrementClaimsUsed();
        original.accrueChunkloadPower(2.5, 1000.0);
        original.grantChunkloadPower(3);
        original.incrementChunkloadsUsed();
        original.incrementChunkloadsUsed();

        CompoundTag tag = original.serialize();
        ClaimPower restored = ClaimPower.deserialize(tag);

        assertEquals(original.getEarnedClaimBlocks(100), restored.getEarnedClaimBlocks(100));
        assertEquals(original.getClaimsUsed(), restored.getClaimsUsed());
        assertEquals(original.getEarnedChunkloadBlocks(100), restored.getEarnedChunkloadBlocks(100));
        assertEquals(original.getChunkloadsUsed(), restored.getChunkloadsUsed());
    }
}
