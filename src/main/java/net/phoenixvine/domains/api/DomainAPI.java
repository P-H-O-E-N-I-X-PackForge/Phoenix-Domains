package net.phoenixvine.domains.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.chunkload.ClaimChunkLoader;
import net.phoenixvine.domains.config.DomainsConfig;
import net.phoenixvine.domains.data.ChunkKey;
import net.phoenixvine.domains.data.Claim;
import net.phoenixvine.domains.data.ClaimFlag;
import net.phoenixvine.domains.data.ClaimPower;
import net.phoenixvine.domains.data.DomainManager;
import net.phoenixvine.domains.ownership.ClaimPermissions;
import net.phoenixvine.domains.ownership.DomainOwnership;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class DomainAPI {

    private DomainAPI() {}

    public static final String FEATURE_CLAIMING = "claiming";
    public static final String FEATURE_CHUNKLOADING = "chunkloading";
    public static final String FEATURE_CLAIM_FLAGS = "claim_flags";

    private static final Map<String, BooleanSupplier> FEATURE_GATES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Integer> DIMENSION_TIERS = new ConcurrentHashMap<>();
    private static final Map<String, Map<ResourceLocation, Integer>> TIER_REQUIREMENTS = new ConcurrentHashMap<>();
    private static final Map<String, Map<ResourceLocation, DomainFeatureState>> FEATURE_STATES = new ConcurrentHashMap<>();

    private static final Set<String> KNOWN_FEATURE_IDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_UNKNOWN_FEATURE_IDS = ConcurrentHashMap.newKeySet();

    static {
        KNOWN_FEATURE_IDS.addAll(List.of(FEATURE_CLAIMING, FEATURE_CHUNKLOADING, FEATURE_CLAIM_FLAGS));
    }

    public static void registerFeatureGate(String featureId, BooleanSupplier check) {
        KNOWN_FEATURE_IDS.add(featureId);
        FEATURE_GATES.put(featureId, check);
    }

    public static void setFeatureEnabled(String featureId, boolean enabled) {
        registerFeatureGate(featureId, () -> enabled);
    }

    public static void clearFeatureGate(String featureId) {
        FEATURE_GATES.remove(featureId);
    }

    public static void setTier(ResourceLocation dimension, int tier) {
        DIMENSION_TIERS.put(dimension, tier);
    }

    public static int getTier(ResourceLocation dimension) {
        return DIMENSION_TIERS.getOrDefault(dimension, 0);
    }

    public static void requireTier(String featureId, ResourceLocation dimension, int requiredTier) {
        KNOWN_FEATURE_IDS.add(featureId);
        TIER_REQUIREMENTS.computeIfAbsent(featureId, id -> new ConcurrentHashMap<>()).put(dimension, requiredTier);
    }

    public static void clearTierRequirement(String featureId, ResourceLocation dimension) {
        Map<ResourceLocation, Integer> perDimension = TIER_REQUIREMENTS.get(featureId);
        if (perDimension != null) perDimension.remove(dimension);
    }

    public static boolean isFeatureEnabled(String featureId, ResourceLocation dimension) {
        warnIfUnknown(featureId);
        if (!checkGate(featureId)) return false;

        Map<ResourceLocation, Integer> perDimension = TIER_REQUIREMENTS.get(featureId);
        if (perDimension == null) return true;

        Integer required = perDimension.get(dimension);
        return required == null || getTier(dimension) >= required;
    }

    private static boolean checkGate(String featureId) {
        BooleanSupplier check = FEATURE_GATES.get(featureId);
        if (check == null) return true;
        return check.getAsBoolean();
    }

    private static void warnIfUnknown(String featureId) {
        if (!KNOWN_FEATURE_IDS.contains(featureId) && WARNED_UNKNOWN_FEATURE_IDS.add(featureId)) {
            PhoenixDomains.LOGGER.debug(
                    "Feature id '{}' was queried but has never been gated, tiered, or given an explicit" +
                            " state — defaulting to enabled. Fine if that's intentional; if not, check for a" +
                            " typo against whatever was supposed to configure it.",
                    featureId);
        }
    }

    public static void setFeatureState(String featureId, ResourceLocation dimension, DomainFeatureState state) {
        KNOWN_FEATURE_IDS.add(featureId);
        FEATURE_STATES.computeIfAbsent(featureId, id -> new ConcurrentHashMap<>()).put(dimension, state);
    }

    public static DomainFeatureState getFeatureState(String featureId, ResourceLocation dimension) {
        warnIfUnknown(featureId);
        Map<ResourceLocation, DomainFeatureState> perDimension = FEATURE_STATES.get(featureId);
        return perDimension == null ? DomainFeatureState.ENABLED :
                perDimension.getOrDefault(dimension, DomainFeatureState.ENABLED);
    }

    public static Optional<UUID> getOwner(MinecraftServer server, ChunkKey key) {
        return manager(server).getClaim(key).map(Claim::getOwner);
    }

    public static boolean isClaimed(MinecraftServer server, ChunkKey key) {
        return manager(server).isClaimed(key);
    }

    public static boolean canInteract(ServerPlayer player, BlockPos pos) {
        ChunkKey key = ChunkKey.of(player.level(), pos.getX(), pos.getZ());
        return manager(player.getServer()).getClaim(key)
                .map(claim -> ClaimPermissions.canBuild(player, claim))
                .orElse(true);
    }

    public static long getAvailableClaimBlocks(MinecraftServer server, UUID token) {
        return manager(server).getOrCreatePower(token).getAvailableClaimBlocks(DomainsConfig.CLAIM_POWER_BASE.get());
    }

    public static long getAvailableChunkloadBlocks(MinecraftServer server, UUID token) {
        return manager(server).getOrCreatePower(token)
                .getAvailableChunkloadBlocks(DomainsConfig.CHUNKLOAD_POWER_BASE.get());
    }

    public static String claim(ServerPlayer player, ChunkKey key) {
        if (!isFeatureEnabled(FEATURE_CLAIMING, key.dimension())) return "feature_disabled";

        DomainManager manager = manager(player.getServer());
        if (manager.isClaimed(key)) return "already_claimed";

        int maxDistance = DomainsConfig.MAX_CLAIM_DISTANCE_CHUNKS.get();
        if (maxDistance > 0) {
            int playerChunkX = player.blockPosition().getX() >> 4;
            int playerChunkZ = player.blockPosition().getZ() >> 4;
            int distance = Math.max(Math.abs(playerChunkX - key.x()), Math.abs(playerChunkZ - key.z()));
            if (distance > maxDistance) return "too_far";
        }

        UUID token = DomainOwnership.tokenFor(player.getUUID());

        int maxClaimed = DomainsConfig.MAX_CLAIMED_CHUNKS_PER_OWNER.get();
        if (maxClaimed > 0 && manager.getClaimsForOwner(token).size() >= maxClaimed) return "too_many_claims";

        ClaimPower power = manager.getOrCreatePower(token);
        if (power.getAvailableClaimBlocks(DomainsConfig.CLAIM_POWER_BASE.get()) <= 0) return "no_power";

        manager.claim(key, token);
        return "ok";
    }

    public static String unclaim(ServerPlayer player, ChunkKey key) {
        DomainManager manager = manager(player.getServer());
        Optional<Claim> claimOpt = manager.getClaim(key);
        if (claimOpt.isEmpty()) return "not_claimed";
        Claim claim = claimOpt.get();
        if (!ClaimPermissions.canManage(player, claim.getOwner())) return "no_permission";

        if (claim.isChunkloaded()) {
            ServerLevel level = levelFor(player.getServer(), key.dimension());
            if (level != null) ClaimChunkLoader.setChunkForced(level, key, false);
        }
        manager.unclaim(key);
        return "ok";
    }

    public static String setChunkloaded(ServerPlayer player, ChunkKey key, boolean chunkloaded) {
        DomainManager manager = manager(player.getServer());
        Optional<Claim> claimOpt = manager.getClaim(key);
        if (claimOpt.isEmpty()) return "not_claimed";
        Claim claim = claimOpt.get();
        if (!ClaimPermissions.canManage(player, claim.getOwner())) return "no_permission";
        if (claim.isChunkloaded() == chunkloaded) return "no_change";

        if (chunkloaded) {
            if (!isFeatureEnabled(FEATURE_CHUNKLOADING, key.dimension())) return "feature_disabled";

            int maxForceloaded = DomainsConfig.MAX_FORCELOADED_CHUNKS_PER_OWNER.get();
            if (maxForceloaded > 0 && manager.getChunkloadedCountForOwner(claim.getOwner()) >= maxForceloaded) {
                return "too_many_forceloaded";
            }

            ClaimPower power = manager.getOrCreatePower(claim.getOwner());
            if (power.getAvailableChunkloadBlocks(DomainsConfig.CHUNKLOAD_POWER_BASE.get()) <= 0) {
                return "no_chunkload_power";
            }
        }

        ServerLevel level = levelFor(player.getServer(), key.dimension());
        if (level == null) return "dimension_not_loaded";

        manager.setChunkloaded(key, chunkloaded);
        ClaimChunkLoader.setChunkForced(level, key, chunkloaded);
        return "ok";
    }

    public static String setFlag(ServerPlayer player, ChunkKey key, ClaimFlag flag, boolean value) {
        if (!isFeatureEnabled(FEATURE_CLAIM_FLAGS, key.dimension())) return "feature_disabled";

        DomainManager manager = manager(player.getServer());
        Optional<Claim> claimOpt = manager.getClaim(key);
        if (claimOpt.isEmpty()) return "not_claimed";
        if (!ClaimPermissions.canManage(player, claimOpt.get().getOwner())) return "no_permission";

        manager.setFlag(key, flag, value);
        return "ok";
    }

    public static void grantClaimPower(UUID token, long amount) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || token == null) return;
        DomainManager manager = manager(server);
        manager.getOrCreatePower(token).grantClaimPower(amount);
        manager.setDirty();
    }

    public static void grantChunkloadPower(UUID token, long amount) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || token == null) return;
        DomainManager manager = manager(server);
        manager.getOrCreatePower(token).grantChunkloadPower(amount);
        manager.setDirty();
    }

    public static void adminSetClaim(MinecraftServer server, ChunkKey key, UUID owner) {
        if (server == null || owner == null) return;
        DomainManager manager = manager(server);
        if (manager.isClaimed(key)) manager.unclaim(key);
        manager.claim(key, owner);
    }

    public static void adminRemoveClaim(MinecraftServer server, ChunkKey key) {
        if (server == null) return;
        DomainManager manager = manager(server);
        Optional<Claim> claimOpt = manager.getClaim(key);
        if (claimOpt.isEmpty()) return;
        if (claimOpt.get().isChunkloaded()) {
            ServerLevel level = levelFor(server, key.dimension());
            if (level != null) ClaimChunkLoader.setChunkForced(level, key, false);
        }
        manager.unclaim(key);
    }

    public static void adminSetChunkloaded(MinecraftServer server, ChunkKey key, boolean chunkloaded) {
        if (server == null) return;
        DomainManager manager = manager(server);
        if (manager.getClaim(key).isEmpty()) return;
        ServerLevel level = levelFor(server, key.dimension());
        if (level == null) return;

        manager.setChunkloaded(key, chunkloaded);
        ClaimChunkLoader.setChunkForced(level, key, chunkloaded);
    }

    private static DomainManager manager(MinecraftServer server) {
        return DomainManager.get(server.overworld());
    }

    private static ServerLevel levelFor(MinecraftServer server, ResourceLocation dimension) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }
}
