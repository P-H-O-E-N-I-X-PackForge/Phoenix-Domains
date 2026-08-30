package net.phoenixvine.domains.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClaimFlagTest {

    @Test
    void byNameFindsAFlagCaseInsensitively() {
        assertSame(ClaimFlag.PVP, ClaimFlag.byName("pvp"));
        assertSame(ClaimFlag.PVP, ClaimFlag.byName("PVP"));
        assertSame(ClaimFlag.PVP, ClaimFlag.byName("PvP"));
    }

    @Test
    void byNameReturnsNullForUnknownFlag() {
        assertNull(ClaimFlag.byName("does_not_exist"));
    }

    @Test
    void everyFlagHasALabel() {
        for (ClaimFlag flag : ClaimFlag.values()) {
            assertNotNull(flag.label(), flag.name() + " should have a display label");
            assertEquals(false, flag.label().isBlank());
        }
    }

    @Test
    void defaultValuesMatchDocumentedIntent() {
        assertEquals(false, ClaimFlag.MOB_GRIEFING.defaultValue());
        assertEquals(true, ClaimFlag.FLUID_FLOW.defaultValue());
        assertEquals(false, ClaimFlag.PVP.defaultValue());
        assertEquals(true, ClaimFlag.ALLY_INTERACT.defaultValue());
    }
}
