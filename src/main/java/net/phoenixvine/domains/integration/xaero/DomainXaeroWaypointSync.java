package net.phoenixvine.domains.integration.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.waypoint.WaypointColor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Drops one Xaero waypoint per claim OWNER currently visible in {@link ClientDomainCache} — not
 * one per claimed chunk, which would spam dozens/hundreds of pins onto the minimap for a single
 * large claimed territory (unlike {@code DomainClaimJourneyMapOverlay}'s per-chunk polygons,
 * which read fine at any density since they're fills, not point markers). This is deliberately
 * the lower-fidelity fallback next to the real chunk-region borders {@code
 * DomainClaimChunkHighlighter} draws — a quick "whose land is this" pointer, not a border.
 * <p>
 * Uses the real, non-mixin custom-waypoints API confirmed against the actual Xaero jar: {@code
 * BuiltInHudModules.MINIMAP.getCurrentSession().getWorldManager().getCustomWaypoints(modId)}
 * returns an {@code Int2ObjectMap<Waypoint>} third-party mods just put/remove into directly.
 */
public final class DomainXaeroWaypointSync {

    private static final ResourceLocation NAMESPACE = PhoenixDomains.id("claim_owners");
    private static final int CHUNKLOADED_RGB = 0xFFD700;
    private static final DomainXaeroWaypointSync INSTANCE = new DomainXaeroWaypointSync();

    private final Map<String, Integer> shownByOwner = new HashMap<>();
    private int nextIndex = 0;
    private ResourceKey<Level> lastDimension;

    private DomainXaeroWaypointSync() {}

    /** Call only after {@code DomainsXaeroMinimapIntegration.isAvailable()} has returned true. */
    public static void sync() {
        INSTANCE.doSync();
    }

    private void doSync() {
        var session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) return;
        Int2ObjectMap<Waypoint> waypoints = session.getWorldManager().getCustomWaypoints(NAMESPACE);

        var level = Minecraft.getInstance().level;
        if (level == null) return;
        ResourceKey<Level> dimension = level.dimension();
        if (!dimension.equals(lastDimension)) {
            clear(waypoints);
            lastDimension = dimension;
        }

        // First claim entry seen per owner name stands in for that owner's whole territory.
        Map<String, S2CDomainSyncPacket.ClaimEntry> representative = new LinkedHashMap<>();
        for (S2CDomainSyncPacket.ClaimEntry entry : ClientDomainCache.claims) {
            representative.putIfAbsent(entry.ownerName(), entry);
        }

        for (Map.Entry<String, S2CDomainSyncPacket.ClaimEntry> e : representative.entrySet()) {
            if (shownByOwner.containsKey(e.getKey())) continue;

            S2CDomainSyncPacket.ClaimEntry claim = e.getValue();
            int color = claim.chunkloaded() ? CHUNKLOADED_RGB : (claim.color() & 0xFFFFFF);
            int index = nextIndex++;
            waypoints.put(index, new Waypoint(claim.x() * 16 + 8, 64, claim.z() * 16 + 8, e.getKey(),
                    initials(e.getKey()), nearestColor(color)));
            shownByOwner.put(e.getKey(), index);
        }

        shownByOwner.entrySet().removeIf(e -> {
            if (representative.containsKey(e.getKey())) return false;
            waypoints.remove((int) e.getValue());
            return true;
        });
    }

    private void clear(Int2ObjectMap<Waypoint> waypoints) {
        for (int index : shownByOwner.values()) waypoints.remove(index);
        shownByOwner.clear();
    }

    private static String initials(String name) {
        return name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
    }

    /** {@link WaypointColor} is a fixed 16-color palette, not arbitrary RGB — nearest match by channel distance. */
    private static WaypointColor nearestColor(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        WaypointColor best = WaypointColor.WHITE;
        int bestDist = Integer.MAX_VALUE;
        for (WaypointColor candidate : WaypointColor.values()) {
            int hex = candidate.getHex();
            int dr = (hex >> 16 & 0xFF) - r, dg = (hex >> 8 & 0xFF) - g, db = (hex & 0xFF) - b;
            int dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }
}
