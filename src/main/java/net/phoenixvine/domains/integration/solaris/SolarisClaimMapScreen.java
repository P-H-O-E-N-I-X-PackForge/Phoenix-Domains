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

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

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

    private void renderTiles(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ResourceLocation dimension = mc.level.dimension().location();

        double worldMinX = viewport.toWorldX(frameX, 0);
        double worldMaxX = viewport.toWorldX(frameX + frameW, 0);
        double worldMinZ = viewport.toWorldZ(frameY, 0);
        double worldMaxZ = viewport.toWorldZ(frameY + frameH, 0);

        float zoom = viewport.getZoom();
        int lod = Math.max(0, (int) Math.ceil(-Math.log(zoom) / Math.log(2) - 1.32));
        if (zoom > 300) lod = Math.max(lod, 2);
        if (zoom > 500) lod = Math.max(lod, 3);
        if (zoom > 1000) lod = Math.max(lod, 4);
        lod = Math.min(14, lod);

        int chunksPerTile = MapTileCache.TILE_CHUNKS << lod;
        int tileWorldSize = (MapTileCache.TILE_CHUNKS * 16) << lod;

        int tileMinX = Math.floorDiv((int) Math.floor(worldMinX) >> 4, chunksPerTile);
        int tileMaxX = Math.floorDiv((int) Math.floor(worldMaxX) >> 4, chunksPerTile);
        int tileMinZ = Math.floorDiv((int) Math.floor(worldMinZ) >> 4, chunksPerTile);
        int tileMaxZ = Math.floorDiv((int) Math.floor(worldMaxZ) >> 4, chunksPerTile);

        for (int tz = tileMinZ; tz <= tileMaxZ; tz++) {
            for (int tx = tileMinX; tx <= tileMaxX; tx++) {
                MapTileCache.TileKey key = new MapTileCache.TileKey(dimension, tx, tz, lod);
                MapTileCache.MapTile tile = MapTileCache.getOrBuildTile(key);

                if (tile == null) continue;

                int tileWorldX = tx * tileWorldSize;
                int tileWorldZ = tz * tileWorldSize;
                int destX = (int) Math.round(viewport.toScreenX(tileWorldX, 0));
                int destY = (int) Math.round(viewport.toScreenY(tileWorldZ, 0));
                int destSizeX = (int) Math.round(viewport.toScreenX(tileWorldX + tileWorldSize, 0)) - destX;
                int destSizeZ = (int) Math.round(viewport.toScreenY(tileWorldZ + tileWorldSize, 0)) - destY;

                g.blit(tile.textureId(), destX, destY, destSizeX, destSizeZ, 0, 0, MapTileCache.TILE_PIXELS,
                        MapTileCache.TILE_PIXELS, MapTileCache.TILE_PIXELS, MapTileCache.TILE_PIXELS);
            }
        }
    }

    // Matches SolarisMapScreen.CHUNK_GRID_MIN_ZOOM — "die past 200" refers to Solaris's own scale
    // bar reading (blocks = 60/zoom), not the raw zoom multiplier, so the actual cutoff is
    // zoom < 60/200 despite this screen not drawing a scale bar of its own.
    private static final float CHUNK_GRID_MIN_ZOOM = 60f / 200f;

    private void drawChunkGrid(GuiGraphics g) {
        if (viewport.getZoom() < CHUNK_GRID_MIN_ZOOM) return;

        int gridColor = 0x44FFFFFF;

        int chunkMinX = (int) Math.floor(viewport.toWorldX(frameX, 0)) >> 4;
        int chunkMaxX = (int) Math.floor(viewport.toWorldX(frameX + frameW, 0)) >> 4;
        for (int cx = chunkMinX; cx <= chunkMaxX + 1; cx++) {
            // Round, not truncate — matches SolarisMapScreen's drawChunkGridWorld, fixing the same
            // grid-vs-terrain 1px drift while panning ("jiggle") that truncation caused there.
            int sx = (int) Math.round(viewport.toScreenX(cx << 4, 0));
            if (sx < frameX || sx > frameX + frameW) continue;
            g.fill(sx, frameY, sx + 1, frameY + frameH, gridColor);
        }

        int chunkMinZ = (int) Math.floor(viewport.toWorldZ(frameY, 0)) >> 4;
        int chunkMaxZ = (int) Math.floor(viewport.toWorldZ(frameY + frameH, 0)) >> 4;
        for (int cz = chunkMinZ; cz <= chunkMaxZ + 1; cz++) {
            int sy = (int) Math.round(viewport.toScreenY(cz << 4, 0));
            if (sy < frameY || sy > frameY + frameH) continue;
            g.fill(frameX, sy, frameX + frameW, sy + 1, gridColor);
        }
    }

    private void highlightChunk(GuiGraphics g, int cx, int cz, int outlineColor) {
        // Matches drawChunkGrid's rounding exactly (both edges independently rounded, same as the
        // grid's own lines) so the highlight box still lines up with the grid it's tracing.
        int x0 = (int) Math.round(viewport.toScreenX(cx << 4, 0));
        int y0 = (int) Math.round(viewport.toScreenY(cz << 4, 0));
        int x1 = (int) Math.round(viewport.toScreenX((cx + 1) << 4, 0));
        int y1 = (int) Math.round(viewport.toScreenY((cz + 1) << 4, 0));
        if (x1 < frameX || x0 > frameX + frameW || y1 < frameY || y0 > frameY + frameH) return;
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, outlineColor);
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
