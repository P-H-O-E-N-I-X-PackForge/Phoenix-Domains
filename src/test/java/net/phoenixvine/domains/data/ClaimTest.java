package net.phoenixvine.domains.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimTest {

    private static final ChunkKey KEY = new ChunkKey(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), 3,
            -7);
    private static final UUID OWNER = UUID.randomUUID();

    @Test
    void getFlagFallsBackToTheFlagsOwnDefaultWhenNoOverrideStored() {
        Claim claim = new Claim(KEY, OWNER);

        assertEquals(ClaimFlag.PVP.defaultValue(), claim.getFlag(ClaimFlag.PVP));
        assertTrue(claim.getFlagOverrides().isEmpty(), "no override should be stored until setFlag is called");
    }

    @Test
    void setFlagToNonDefaultValueStoresAnOverride() {
        Claim claim = new Claim(KEY, OWNER);

        claim.setFlag(ClaimFlag.PVP, true);

        assertTrue(claim.getFlag(ClaimFlag.PVP));
        assertTrue(claim.getFlagOverrides().containsKey(ClaimFlag.PVP));
    }

    @Test
    void setFlagBackToDefaultValueRemovesTheOverride() {
        Claim claim = new Claim(KEY, OWNER);
        claim.setFlag(ClaimFlag.PVP, true);

        claim.setFlag(ClaimFlag.PVP, ClaimFlag.PVP.defaultValue());

        assertFalse(claim.getFlagOverrides().containsKey(ClaimFlag.PVP),
                "setting a flag back to its own default should clear the stored override, not keep a redundant one");
        assertEquals(ClaimFlag.PVP.defaultValue(), claim.getFlag(ClaimFlag.PVP));
    }

    @Test
    void clearFlagRemovesAnyOverride() {
        Claim claim = new Claim(KEY, OWNER);
        claim.setFlag(ClaimFlag.EXPLOSIONS, true);

        claim.clearFlag(ClaimFlag.EXPLOSIONS);

        assertEquals(ClaimFlag.EXPLOSIONS.defaultValue(), claim.getFlag(ClaimFlag.EXPLOSIONS));
    }

    @Test
    void serializeDeserializeRoundTripsKeyOwnerAndFlags() {
        Claim original = new Claim(KEY, OWNER);
        original.setChunkloaded(true);
        original.setFlag(ClaimFlag.PVP, true);
        original.setFlag(ClaimFlag.FLUID_FLOW, false);

        CompoundTag tag = original.serialize();
        Claim restored = Claim.deserialize(tag);

        assertEquals(original.getKey(), restored.getKey());
        assertEquals(original.getOwner(), restored.getOwner());
        assertEquals(original.getClaimedAt(), restored.getClaimedAt());
        assertTrue(restored.isChunkloaded());
        assertTrue(restored.getFlag(ClaimFlag.PVP));
        assertFalse(restored.getFlag(ClaimFlag.FLUID_FLOW));
    }

    @Test
    void deserializeIgnoresUnrecognizedFlagNames() {
        Claim original = new Claim(KEY, OWNER);
        CompoundTag tag = original.serialize();
        CompoundTag flags = tag.getCompound("flags");
        flags.putBoolean("SOME_FLAG_THAT_NO_LONGER_EXISTS", true);
        tag.put("flags", flags);

        Claim restored = Claim.deserialize(tag);

        assertTrue(restored.getFlagOverrides().isEmpty(),
                "an unrecognized flag name in saved data should be skipped, not crash deserialize");
    }
}
