package net.phoenixvine.domains.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.data.ChunkKey;
import net.phoenixvine.domains.data.DomainManager;

import java.util.UUID;

/**
 * Real-server coverage for DomainManager.get(ServerLevel) - the one part of DomainManager that
 * JUnit can't reach (see DomainManagerTest's doc comment: everything else is exercised directly
 * against `new DomainManager()`). This covers the SavedData wiring itself: that get() is a
 * genuine per-overworld singleton and that claims made through the live, server-anchored
 * instance behave the same as the in-memory JUnit coverage. Run via `./gradlew runGameTestServer`.
 */
@GameTestHolder(PhoenixDomains.MOD_ID)
@PrefixGameTestTemplate(false)
public class DomainManagerGameTests {

    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void getReturnsTheSameSingletonAcrossCalls(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();

        DomainManager first = DomainManager.get(overworld);
        DomainManager second = DomainManager.get(overworld);

        helper.assertTrue(first == second,
                "DomainManager.get() should return the same cached instance, not a fresh one");

        helper.succeed();
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void claimingThroughTheLiveManagerIsVisibleOnASecondFetch(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        UUID owner = UUID.randomUUID();
        ChunkKey key = new ChunkKey(ResourceLocation.fromNamespaceAndPath("phoenix_domains", "gametest_dim"),
                12345, -6789);

        try {
            boolean claimed = DomainManager.get(overworld).claim(key, owner);
            helper.assertTrue(claimed, "claiming a fresh chunk key should succeed");

            DomainManager refetched = DomainManager.get(overworld);
            helper.assertTrue(refetched.isClaimed(key),
                    "a claim made through the live manager should be visible on a fresh get()");
            helper.assertTrue(refetched.getClaim(key).map(c -> c.getOwner().equals(owner)).orElse(false),
                    "the refetched claim should report the same owner it was claimed with");

            helper.succeed();
        } finally {
            DomainManager.get(overworld).unclaim(key);
        }
    }
}
