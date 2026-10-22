package net.phoenixvine.domains.integration.xaero;

import xaero.map.WorldMapSession;
import xaero.map.world.MapDimension;

/**
 * Xaero's World Map caches each region's highlight hash and only redraws a region when the hash it calculates changes.
 * The cache has to be cleared when the claims change, or a newly claimed (or unclaimed) chunk keeps its old colour.
 */
public final class DomainXaeroHighlightRefresh {

    private DomainXaeroHighlightRefresh() {}

    public static void refresh() {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || !session.isUsable()) return;
        var world = session.getMapProcessor().getMapWorld();
        if (world == null) return;
        MapDimension dimension = world.getCurrentDimension();
        if (dimension == null) return;
        dimension.getHighlightHandler().clearCachedHashes();
    }
}
