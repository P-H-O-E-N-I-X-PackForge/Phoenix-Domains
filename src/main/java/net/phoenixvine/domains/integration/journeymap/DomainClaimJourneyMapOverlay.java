package net.phoenixvine.domains.integration.journeymap;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;

import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.DisplayType;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.model.MapPolygon;
import journeymap.client.api.model.ShapeProperties;
import journeymap.client.api.util.PolygonHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class DomainClaimJourneyMapOverlay {

    private static final int FILL_OPACITY_PERCENT = 40;
    private static final int TINT_ALPHA = 0x99;
    private static final int CHUNKLOADED_RGB = 0xFFD700;

    private final Map<Long, PolygonOverlay> shown = new HashMap<>();
    private ResourceKey<Level> lastDimension = null;

    void sync(IClientAPI api) {
        if (!api.playerAccepts(PhoenixDomains.MOD_ID, DisplayType.Polygon)) return;

        var level = Minecraft.getInstance().level;
        if (level == null) return;
        ResourceKey<Level> dimension = level.dimension();

        if (!dimension.equals(lastDimension)) {
            clear(api);
            lastDimension = dimension;
        }

        Set<Long> stillClaimed = new HashSet<>();
        for (S2CDomainSyncPacket.ClaimEntry entry : ClientDomainCache.claims) {
            long key = packKey(entry.x(), entry.z());
            stillClaimed.add(key);

            int color = entry.chunkloaded() ? CHUNKLOADED_RGB : (entry.color() & 0xFFFFFF);
            PolygonOverlay existing = shown.get(key);
            if (existing != null && colorOf(existing) == color) continue;

            PolygonOverlay overlay = buildOverlay(entry, color, dimension);
            try {
                api.show(overlay);
                shown.put(key, overlay);
            } catch (Exception e) {
                PhoenixDomains.LOGGER.warn("Failed to show a claim polygon on JourneyMap", e);
            }
        }

        shown.keySet().removeIf(key -> {
            if (stillClaimed.contains(key)) return false;
            api.remove(shown.get(key));
            return true;
        });
    }

    void clear(IClientAPI api) {
        shown.values().forEach(api::remove);
        shown.clear();
    }

    private static PolygonOverlay buildOverlay(S2CDomainSyncPacket.ClaimEntry entry, int color,
                                               ResourceKey<Level> dimension) {
        ShapeProperties shapeProps = new ShapeProperties()
                .setStrokeWidth(2)
                .setStrokeColor(color)
                .setFillColor(color)
                .setFillOpacity(FILL_OPACITY_PERCENT / 100f)
                .setStrokeOpacity((TINT_ALPHA & 0xFF) / 255f);

        MapPolygon polygon = PolygonHelper.createChunkPolygon(entry.x(), 0, entry.z());
        String displayId = entry.x() + "_" + entry.z();
        PolygonOverlay overlay = new PolygonOverlay(PhoenixDomains.MOD_ID, displayId, dimension, shapeProps, polygon);
        overlay.setDimension(dimension);
        overlay.setTitle(entry.ownerName());
        return overlay;
    }

    private static int colorOf(PolygonOverlay overlay) {
        return overlay.getShapeProperties().getFillColor();
    }

    private static long packKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
