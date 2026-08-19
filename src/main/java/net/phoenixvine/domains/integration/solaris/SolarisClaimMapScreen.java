package net.phoenixvine.domains.integration.solaris;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.network.C2SDomainActionPacket;
import net.phoenixvine.domains.network.DomainNetwork;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;
import net.phoenixvine.solaris.client.SolarisThemeUtils;
import net.phoenixvine.solaris.client.render.MapTileCache;
import net.phoenixvine.solaris.client.render.MapViewport;
import net.phoenixvine.solaris.client.render.VanillaPanel;
import net.phoenixvine.solaris.config.SolarisConfig;

/**
 * The claim screen used when Solaris is installed: real sampled terrain, rendered through
 * {@link MapTileCache} — the same chunk-content-addressed tile system {@code SolarisMapScreen}'s
 * own flat view uses, NOT the older fixed-window {@code SolarisTexture} buffer this screen
 * originally used. That buffer scrolled/rebuilt around a moving player-centered anchor, so
 * panning past its radius regressed already-explored chunks back to fog and it never picked up
 * anything added to Solaris after the tile system replaced it (starfield/phoenix/cloud unexplored
 * styles, theme-aware rendering, free panning across everything explored). Tiles have no notion of
 * "camera position" at all — a tile's content depends only on its own chunks' persisted data — so
 * this screen can pan anywhere already explored with nothing ever scrolling out.
 * <p>
 * Claimed chunks are tinted via {@link DomainClaimOverlay}, a normal {@code SolarisOverlay}
 * registered through {@link DomainsSolarisIntegration#init()} — overlays are chunk-based
 * ({@code colorAt(dimension, chunkX, chunkZ)}), so they apply identically whether the caller is
 * {@code SolarisTexture} or {@code MapTileCache}; nothing about this rewrite touches that class.
 * <p>
 * Lives in {@code integration.solaris} (not {@code client.map}) because, like every other
 * class in this package, it directly references Solaris types and must never be
 * instantiated except behind {@link DomainsSolarisIntegration#isAvailable()}.
 * <p>
 * Controls: left-click claims, right-click unclaims — separate dedicated buttons (not one
 * toggle) specifically so you can hold a button down and drag across many chunks to mass-
 * claim/unclaim without a chunk you're just passing over flipping to the opposite of what you
 * want. Shift+left-click claims AND chunkloads in one action (or just turns chunkload on);
 * shift+right-click turns chunkload off without unclaiming. Panning is on middle-click-drag
 * instead of left, since left is now a direct claim action — this also matters for a reason
 * beyond input conflicts: this map lets you pan and see chunks far past where you're actually
 * standing, and letting that double as the claim button would make it too easy to claim land
 * you've never traveled to. ({@link net.phoenixvine.domains.api.DomainAPI#claim} enforces a
 * max distance server-side regardless, but the controls shouldn't invite trying to abuse it.)
 */
@OnlyIn(Dist.CLIENT)
public class SolarisClaimMapScreen extends Screen {

    private static final int MARGIN = 20;
    private static final int SIDEBAR_W = 150;
    private static final int LINE_H = 10;

    private final MapViewport viewport = new MapViewport(
            SolarisConfig.ZOOM_MIN.get().floatValue(), SolarisConfig.ZOOM_MAX.get().floatValue());
    private boolean panning = false;
    private boolean leftHeld = false;
    private boolean rightHeld = false;
    private int lastActionChunkX = Integer.MIN_VALUE;
    private int lastActionChunkZ = Integer.MIN_VALUE;
    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;
    private boolean notifiedViewOnly = false;

    // The screen to return to (via onClose, below) when this one closes - e.g. the inventory
    // screen if this was opened from the cross-suite HUD bar button while inventory was open, or
    // null if there was nothing open beforehand (opened from plain gameplay via keybind).
    private final Screen parent;

    public SolarisClaimMapScreen() {
        this(null);
    }

    public SolarisClaimMapScreen(Screen parent) {
        super(Component.translatable("domains.map.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();

        frameX = MARGIN;
        frameY = MARGIN;
        frameW = width - 2 * MARGIN - SIDEBAR_W - MARGIN;
        frameH = height - 2 * MARGIN;

        if (mc.player != null) {
            double frameCenterX = frameX + frameW / 2.0;
            double frameCenterY = frameY + frameH / 2.0;
            viewport.setOffset(frameCenterX - mc.player.getX() * viewport.getZoom(),
                    frameCenterY - mc.player.getZ() * viewport.getZoom());
        }

        DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.requestSync(SolarisConfig.MAP_RADIUS_CHUNKS.get()));
    }

    /**
     * Returns to whichever screen was open before this one (e.g. the inventory screen), instead
     * of vanilla {@link Screen}'s default of dropping to the world.
     */
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /** World chunk coords under a screen position, or {@code null} if outside the map frame. */
    private int[] chunkAt(double mx, double my) {
        if (mx < frameX || mx > frameX + frameW || my < frameY || my > frameY + frameH) return null;
        int blockX = (int) Math.floor(viewport.toWorldX(mx, 0));
        int blockZ = (int) Math.floor(viewport.toWorldZ(my, 0));
        return new int[] { blockX >> 4, blockZ >> 4 };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);

        VanillaPanel.draw(g, frameX - 8, frameY - 8, frameW + 16, frameH + 16, SolarisThemeUtils.C_BORDER);
        g.enableScissor(frameX, frameY, frameX + frameW, frameY + frameH);
        g.fill(frameX, frameY, frameX + frameW, frameY + frameH, SolarisThemeUtils.C_BG);

        renderTiles(g);
        drawChunkGrid(g);

        int[] hovered = chunkAt(mx, my);
        S2CDomainSyncPacket.ClaimEntry hoveredEntry = hovered != null ?
                ClientDomainCache.entryAt(hovered[0], hovered[1]) : null;
        if (hovered != null) highlightChunk(g, hovered[0], hovered[1], 0x55FFFFFF);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            highlightChunk(g, mc.player.blockPosition().getX() >> 4, mc.player.blockPosition().getZ() >> 4,
                    0xFFFFFFFF);
        }

        g.disableScissor();

        super.render(g, mx, my, partialTick);

        g.drawCenteredString(font, title, frameX + frameW / 2, 6, SolarisThemeUtils.C_ACCENT);

        renderSidebar(g, hovered, hoveredEntry);
    }

    /**
     * Tiles sourced directly from {@link MapTileCache}, independent of player/camera position —
     * same approach and same tile-boundary-alignment math as {@code SolarisMapScreen}'s own
     * {@code renderFlatMapTiles}.
     * <p>
     * BLOCKED: LOD implementation waiting on published Solaris update.
     * Solaris now supports 4-arg TileKey(dimension, tileX, tileZ, lod) to enable aggressive LOD
     * at high zoom. Once published: calculate lod = lodForZoom(viewport.getZoom()), scale
     * chunksPerTile and tileWorldSize by (1 << lod), and pass lod to TileKey. This will reduce
     * lag at zoom > 300+ by rendering coarser detail tiles instead of full-resolution.
     */
    private void renderTiles(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ResourceLocation dimension = mc.level.dimension().location();

        double worldMinX = viewport.toWorldX(frameX, 0);
        double worldMaxX = viewport.toWorldX(frameX + frameW, 0);
        double worldMinZ = viewport.toWorldZ(frameY, 0);
        double worldMaxZ = viewport.toWorldZ(frameY + frameH, 0);

        int tileMinX = Math.floorDiv((int) Math.floor(worldMinX) >> 4, MapTileCache.TILE_CHUNKS);
        int tileMaxX = Math.floorDiv((int) Math.floor(worldMaxX) >> 4, MapTileCache.TILE_CHUNKS);
        int tileMinZ = Math.floorDiv((int) Math.floor(worldMinZ) >> 4, MapTileCache.TILE_CHUNKS);
        int tileMaxZ = Math.floorDiv((int) Math.floor(worldMaxZ) >> 4, MapTileCache.TILE_CHUNKS);

        for (int tz = tileMinZ; tz <= tileMaxZ; tz++) {
            for (int tx = tileMinX; tx <= tileMaxX; tx++) {
                MapTileCache.TileKey key = new MapTileCache.TileKey(dimension, tx, tz);
                MapTileCache.MapTile tile = MapTileCache.getOrBuildTile(key);
                // null means the persisted store isn't finished loading yet — skip drawing this
                // tile for now rather than building it with an incomplete picture; it'll be ready
                // within a frame or two once the (async, one-time-per-dimension) load completes.
                if (tile == null) continue;

                int tileWorldX = tx * MapTileCache.TILE_CHUNKS * 16;
                int tileWorldZ = tz * MapTileCache.TILE_CHUNKS * 16;
                int destX = (int) Math.round(viewport.toScreenX(tileWorldX, 0));
                int destY = (int) Math.round(viewport.toScreenY(tileWorldZ, 0));
                int destSizeX = (int) Math.round(viewport.toScreenX(tileWorldX + MapTileCache.TILE_PIXELS, 0)) -
                        destX;
                int destSizeZ = (int) Math.round(viewport.toScreenY(tileWorldZ + MapTileCache.TILE_PIXELS, 0)) -
                        destY;

                g.blit(tile.textureId(), destX, destY, destSizeX, destSizeZ, 0, 0, MapTileCache.TILE_PIXELS,
                        MapTileCache.TILE_PIXELS, MapTileCache.TILE_PIXELS, MapTileCache.TILE_PIXELS);
            }
        }
    }

    /** Chunk boundary lines derived from world chunk coordinates, not a fixed texture window. */
    private void drawChunkGrid(GuiGraphics g) {
        int gridColor = 0x22FFFFFF;

        int chunkMinX = (int) Math.floor(viewport.toWorldX(frameX, 0)) >> 4;
        int chunkMaxX = (int) Math.floor(viewport.toWorldX(frameX + frameW, 0)) >> 4;
        for (int cx = chunkMinX; cx <= chunkMaxX + 1; cx++) {
            int sx = (int) viewport.toScreenX(cx << 4, 0);
            if (sx < frameX || sx > frameX + frameW) continue;
            g.fill(sx, frameY, sx + 1, frameY + frameH, gridColor);
        }

        int chunkMinZ = (int) Math.floor(viewport.toWorldZ(frameY, 0)) >> 4;
        int chunkMaxZ = (int) Math.floor(viewport.toWorldZ(frameY + frameH, 0)) >> 4;
        for (int cz = chunkMinZ; cz <= chunkMaxZ + 1; cz++) {
            int sy = (int) viewport.toScreenY(cz << 4, 0);
            if (sy < frameY || sy > frameY + frameH) continue;
            g.fill(frameX, sy, frameX + frameW, sy + 1, gridColor);
        }
    }

    private void highlightChunk(GuiGraphics g, int cx, int cz, int outlineColor) {
        int x0 = (int) viewport.toScreenX(cx << 4, 0);
        int y0 = (int) viewport.toScreenY(cz << 4, 0);
        int s = (int) (16 * viewport.getZoom());
        if (x0 + s < frameX || x0 > frameX + frameW || y0 + s < frameY || y0 > frameY + frameH) return;
        g.renderOutline(x0, y0, s, s, outlineColor);
    }

    private void renderSidebar(GuiGraphics g, int[] hovered, S2CDomainSyncPacket.ClaimEntry hoveredEntry) {
        int x = width - SIDEBAR_W - MARGIN;
        int y = MARGIN;
        int w = SIDEBAR_W;
        int bottom = height - MARGIN;

        VanillaPanel.draw(g, x - 8, y - 8, w + 16, bottom - y + 16, SolarisThemeUtils.C_BORDER);
        g.fill(x, y, x + w, bottom, SolarisThemeUtils.C_PANEL);

        int tx = x + 8;
        int maxTextW = w - 16;
        int ty = y + 8;

        ty = drawWrapped(g, title, tx, ty, maxTextW, SolarisThemeUtils.C_ACCENT) + 2;

        ty = drawWrapped(g, Component.translatable("domains.map.claim_blocks", ClientDomainCache.usedClaimBlocks,
                ClientDomainCache.availableClaimBlocks), tx, ty, maxTextW, SolarisThemeUtils.C_TEXT);
        ty = drawWrapped(g, Component.translatable("domains.map.chunkload_blocks",
                ClientDomainCache.usedChunkloadBlocks, ClientDomainCache.availableChunkloadBlocks), tx, ty,
                maxTextW, SolarisThemeUtils.C_TEXT) + 4;

        ty = divider(g, tx, ty, x + w - 8);

        if (hovered != null) {
            ty = drawWrapped(g, Component.literal("Chunk " + hovered[0] + ", " + hovered[1]), tx, ty, maxTextW,
                    SolarisThemeUtils.C_TEXT);
            if (hoveredEntry == null) {
                ty = drawWrapped(g, Component.translatable("domains.hud.wilderness"), tx, ty, maxTextW,
                        SolarisThemeUtils.C_DIM);
            } else {
                ty = drawWrapped(g, Component.literal(hoveredEntry.ownerName()), tx, ty, maxTextW,
                        hoveredEntry.color());
                Component chunkloadText = Component.translatable(
                        hoveredEntry.chunkloaded() ? "domains.map.chunkloaded" : "domains.map.not_chunkloaded");
                ty = drawWrapped(g, chunkloadText, tx, ty, maxTextW,
                        hoveredEntry.chunkloaded() ? 0xFFD700 : SolarisThemeUtils.C_DIM);
                if (isMine(hoveredEntry)) {
                    ty = drawWrapped(g, Component.translatable("domains.map.yours"), tx, ty, maxTextW,
                            SolarisThemeUtils.C_ACCENT);
                }
            }
        } else {
            ty = drawWrapped(g, Component.translatable("domains.map.hover_hint"), tx, ty, maxTextW,
                    SolarisThemeUtils.C_DIM);
        }
        ty += 4;

        ty = divider(g, tx, ty, x + w - 8);

        ty = drawWrapped(g, Component.translatable("domains.map.hint_claim"), tx, ty, maxTextW,
                SolarisThemeUtils.C_DIM);
        ty = drawWrapped(g, Component.translatable("domains.map.hint_unclaim"), tx, ty, maxTextW,
                SolarisThemeUtils.C_DIM);
        ty = drawWrapped(g, Component.translatable("domains.map.hint_chunkload"), tx, ty, maxTextW,
                SolarisThemeUtils.C_DIM);
        drawWrapped(g, Component.translatable("domains.map.hint_pan_middle"), tx, ty, maxTextW,
                SolarisThemeUtils.C_DIM);
    }

    private int divider(GuiGraphics g, int x1, int y, int x2) {
        g.fill(x1, y, x2, y + 1, SolarisThemeUtils.C_BORDER2);
        return y + 8;
    }

    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            g.drawString(font, line, x, y, color, false);
            y += LINE_H;
        }
        return y + 2;
    }

    /**
     * {@code VISIBLE}-only access refuses claim/unclaim/chunkload clicks here, client-side.
     * Checks the integration state using our safe proxy class instead of directly loading Solaris classes.
     */
    private boolean canManage() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return true;
        boolean enabled = DomainsSolarisIntegration.claimMapState(mc.level.dimension().location())
                .atLeast(DomainsSolarisIntegration.FeatureState.ENABLED);
        if (!enabled && !notifiedViewOnly) {
            notifiedViewOnly = true;
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("domains.map.view_only"), true);
            }
        }
        return enabled;
    }

    private boolean isMine(S2CDomainSyncPacket.ClaimEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && entry.ownerName().equals(mc.player.getName().getString());
    }

    /**
     * Claims (or shift: claims+chunkloads / turns chunkload on) whatever's hovered — no-ops if
     * we already acted on this exact chunk since the button went down, so holding the button
     * and dragging across many chunks claims each one once, not every frame.
     */
    private void performClaimAction(double mx, double my, boolean shift) {
        if (!canManage()) return;
        int[] hovered = chunkAt(mx, my);
        if (hovered == null || (hovered[0] == lastActionChunkX && hovered[1] == lastActionChunkZ)) return;
        lastActionChunkX = hovered[0];
        lastActionChunkZ = hovered[1];

        int cx = hovered[0];
        int cz = hovered[1];
        S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(cx, cz);
        if (shift) {
            if (entry == null) {
                DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.claim(cx, cz));
                DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.setChunkloaded(cx, cz, true));
            } else {
                DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.setChunkloaded(cx, cz, true));
            }
        } else if (entry == null) {
            DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.claim(cx, cz));
        }
    }

    /**
     * Unclaims (or shift: just removes chunkload, keeping the claim) whatever's hovered — same
     * once-per-chunk-per-drag guard as {@link #performClaimAction}.
     */
    private void performUnclaimAction(double mx, double my, boolean shift) {
        if (!canManage()) return;
        int[] hovered = chunkAt(mx, my);
        if (hovered == null || (hovered[0] == lastActionChunkX && hovered[1] == lastActionChunkZ)) return;
        lastActionChunkX = hovered[0];
        lastActionChunkZ = hovered[1];

        int cx = hovered[0];
        int cz = hovered[1];
        S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(cx, cz);
        if (entry == null) return;
        if (shift) {
            DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.setChunkloaded(cx, cz, false));
        } else {
            DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.unclaim(cx, cz));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        if (button == 0) {
            leftHeld = true;
            lastActionChunkX = Integer.MIN_VALUE;
            lastActionChunkZ = Integer.MIN_VALUE;
            performClaimAction(mx, my, hasShiftDown());
            return true;
        }
        if (button == 1) {
            rightHeld = true;
            lastActionChunkX = Integer.MIN_VALUE;
            lastActionChunkZ = Integer.MIN_VALUE;
            performUnclaimAction(mx, my, hasShiftDown());
            return true;
        }
        if (button == 2) {
            panning = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) leftHeld = false;
        if (button == 1) rightHeld = false;
        if (button == 2) panning = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning) {
            viewport.pan(dx, dy);
            return true;
        }
        if (leftHeld) {
            performClaimAction(mx, my, hasShiftDown());
            return true;
        }
        if (rightHeld) {
            performUnclaimAction(mx, my, hasShiftDown());
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return viewport.adjustZoomToAnchor(delta, mx, my, 0, 0) || super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
