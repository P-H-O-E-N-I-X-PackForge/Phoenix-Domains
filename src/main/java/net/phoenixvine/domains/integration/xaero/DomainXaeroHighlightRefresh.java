package net.phoenixvine.domains.integration.xaero;

import xaero.map.WorldMapSession;
import xaero.map.world.MapDimension;

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
