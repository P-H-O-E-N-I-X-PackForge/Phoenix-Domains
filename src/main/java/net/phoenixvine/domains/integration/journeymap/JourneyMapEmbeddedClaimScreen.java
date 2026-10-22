package net.phoenixvine.domains.integration.journeymap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.client.DomainsThemePalette;
import net.phoenixvine.domains.integration.claimclick.ClaimClickActions;
import net.phoenixvine.domains.network.C2SDomainActionPacket;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;
import net.phoenixvine.wiki.PhoenixWikiAPI;
import net.phoenixvine.wiki.client.screen.WikiTheme;
import net.phoenixvine.wiki.theme.PhoenixTheme;
import net.phoenixvine.wiki.theme.PhoenixThemeEditorScreen;

import journeymap.api.v2.client.display.Context;
import journeymap.client.JourneymapClient;
import journeymap.client.io.FileHandler;
import journeymap.client.model.map.MapState;
import journeymap.client.model.map.MapType;
import journeymap.client.render.draw.DrawUtil;
import journeymap.client.render.draw.RadarDrawStepFactory;
import journeymap.client.render.draw.WaypointDrawStepFactory;
import journeymap.client.render.map.MapRenderer;

import java.awt.geom.Point2D;
import java.io.File;

@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
public class JourneyMapEmbeddedClaimScreen extends Screen {

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    // JourneyMap 6's zoom is not a small level: it is pixels per 512 blocks (so 512 is one pixel per block) and its
    // own clamp is 2 to 16384. Small numbers make its pixel-to-block maths divide by nearly nothing.
    private static final int MIN_ZOOM = 64;
    private static final int MAX_ZOOM = 8192;
    private static final int DEFAULT_ZOOM = 1024;
    private static final double ZOOM_STEP = 1.25;
    private static final int SIDEBAR_W = 150;
    private static final int MARGIN = 12;
    private static final int LINE_H = 10;

    public static boolean tryOpen() {
        return tryOpen(null);
    }

    public static boolean tryOpen(Screen returnTo) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        File worldDir = FileHandler.getJMWorldDir(mc);
        if (worldDir == null) return false;
        mc.setScreen(new JourneyMapEmbeddedClaimScreen(worldDir, returnTo));
        return true;
    }

    private final File worldDir;

    private final Screen parent;
    private MapRenderer gridRenderer;
    private MapState mapState;
    private final WaypointDrawStepFactory waypointFactory = new WaypointDrawStepFactory();
    private final RadarDrawStepFactory radarFactory = new RadarDrawStepFactory();
    private MapType mapType;
    private int zoom = DEFAULT_ZOOM;
    private double centerBlockX;
    private double centerBlockZ;
    private boolean leftHeld = false;
    private boolean rightHeld = false;
    private boolean panning = false;
    private int lastActionChunkX = Integer.MIN_VALUE;
    private int lastActionChunkZ = Integer.MIN_VALUE;
    private int hoverChunkX = Integer.MIN_VALUE;
    private int hoverChunkZ;

    private JourneyMapEmbeddedClaimScreen(File worldDir, Screen parent) {
        super(Component.translatable("domains.map.title"));
        this.worldDir = worldDir;
        this.parent = parent;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // JourneyMap's renderer draws nothing until it has a MapState (it reads the map type from it every frame),
        // so set one up the way JourneyMap's own fullscreen map does
        mapState = new MapState();
        mapState.refresh(mc, mc.player, JourneymapClient.getInstance().getFullMapProperties());
        mapType = mapState.setMapType(MapType.day(mc.player.level().dimension()));
        centerBlockX = mc.player.getX();
        centerBlockZ = mc.player.getZ();

        MapRenderer.setEnabled(true);
        gridRenderer = new MapRenderer(Context.UI.Fullscreen);
        gridRenderer.setContext(mapState);

        recenter();
        try {
            gridRenderer.updateTiles(mapType, zoom, true);
            gridRenderer.updateUIState(true);
        } catch (Throwable t) {
            PhoenixDomains.LOGGER.error("Failed to prepare the embedded JourneyMap claim map.", t);
        }

        net.neoforged.neoforge.network.PacketDistributor.sendToServer(C2SDomainActionPacket.requestSync(16));
    }

    private int frames;

    /**
     * Draws what JourneyMap draws over the tiles: the claim polygons the Domains plugin shows through its API are in
     * the map state's draw steps, not in the tiles.
     */
    private void drawOverlays(GuiGraphics g) {
        try {
            // JourneyMap keeps the overlay draw steps it prepared for the current view; a claim that was added or
            // removed has to make it prepare them again
            if (ClientDomainCache.version != lastClaimVersion) {
                lastClaimVersion = ClientDomainCache.version;
                DomainJourneyMapPlugin.sync();
                gridRenderer.markOverlaySurfaceDirty();
                gridRenderer.updateUIState(true);
            }
            mapState.generateDrawSteps(minecraft, gridRenderer, waypointFactory, radarFactory,
                    JourneymapClient.getInstance().getFullMapProperties(), false);
            gridRenderer.draw(g, minecraft.renderBuffers().bufferSource(), mapState.getDrawSteps(), 0.0, 0.0, 1.0, 0.0);
            gridRenderer.draw(g, minecraft.renderBuffers().bufferSource(), mapState.getDrawWaypointSteps(), 0.0, 0.0,
                    1.0, 0.0);
        } catch (Throwable t) {
            if (!loggedOverlayFailure) {
                loggedOverlayFailure = true;
                PhoenixDomains.LOGGER.error("Failed to draw the claim overlays on the embedded JourneyMap map.", t);
            }
        }
    }

    private boolean loggedOverlayFailure;
    private int lastClaimVersion = -1;

    /**
     * JourneyMap's renderer works in window pixels, while the screen and the mouse are in gui-scaled units, so a
     * mouse position has to be scaled up before it is asked which block it is over.
     */
    private Point2D.Double pixel(double guiX, double guiY) {
        double scale = minecraft.getWindow().getScreenWidth() / (double) Math.max(1, width);
        return new Point2D.Double(guiX * scale, guiY * scale);
    }

    private void logDiagnostics() {
        try {
            PhoenixDomains.LOGGER.info(
                    "[Domains] JourneyMap embedded map diagnostics: center={},{} zoom={} size={}x{} grid={}" +
                            " unloadedTile={} mapType={} worldDir={} uiState={} screen={}x{} window={}x{}",
                    gridRenderer.getCenterBlockX(), gridRenderer.getCenterBlockZ(), gridRenderer.getZoom(),
                    gridRenderer.getWidth(), gridRenderer.getHeight(), gridRenderer.getGridSize(),
                    gridRenderer.hasUnloadedTile(), gridRenderer.getMapType(), worldDir,
                    gridRenderer.getUIState(), width, height, minecraft.getWindow().getScreenWidth(),
                    minecraft.getWindow().getScreenHeight());
        } catch (Throwable t) {
            PhoenixDomains.LOGGER.error("[Domains] JourneyMap embedded map diagnostics failed.", t);
        }
    }

    private void recenter() {
        if (gridRenderer == null || mapType == null) return;
        try {
            gridRenderer.center(worldDir, mapType, centerBlockX, centerBlockZ, zoom);
        } catch (Throwable t) {
            PhoenixDomains.LOGGER.error("Failed to center the embedded JourneyMap claim map.", t);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g, mx, my, partialTick);
        if (gridRenderer != null && mapType != null) {

            // JourneyMap lays the map out in window pixels (as its own fullscreen map does), so the projection has
            // to be window-sized while it draws, and gui-sized again afterwards
            g.flush();
            DrawUtil.sizeDisplay(minecraft.getWindow().getScreenWidth(), minecraft.getWindow().getScreenHeight());
            try {
                gridRenderer.setContext(mapState);
                // the first centre ran before the renderer had measured the window and the grid was not built, so
                // keep centring for a few frames until it is
                if (frames < 8) recenter();
                gridRenderer.clearGlErrors(false);
                gridRenderer.updateRotation(g, 0.0);
                gridRenderer.updateTiles(mapType, zoom, false);
                gridRenderer.render(g, minecraft.renderBuffers().bufferSource(), 0.0, 0.0, 0.8f, true);
                drawOverlays(g);
                minecraft.renderBuffers().bufferSource().endBatch();
                if (++frames == 90) logDiagnostics();
            } finally {
                DrawUtil.sizeDisplay(width, height);
            }

            BlockPos pos = gridRenderer.getBlockAtPixel(pixel(mx, my));
            if (pos != null) {
                hoverChunkX = pos.getX() >> 4;
                hoverChunkZ = pos.getZ() >> 4;
            } else {
                hoverChunkX = Integer.MIN_VALUE;
            }
        }

        S2CDomainSyncPacket.ClaimEntry hoveredEntry = hoverChunkX != Integer.MIN_VALUE ?
                ClientDomainCache.entryAt(hoverChunkX, hoverChunkZ) : null;
        renderSidebar(g, mx, my, hoveredEntry);

        super.render(g, mx, my, partialTick);
    }

    private void renderSidebar(GuiGraphics g, int mx, int my, S2CDomainSyncPacket.ClaimEntry hoveredEntry) {
        DomainsThemePalette.refresh();
        int x = width - SIDEBAR_W - MARGIN;
        int y = MARGIN;
        int w = SIDEBAR_W;
        int bottom = height - MARGIN;

        g.fill(x, y, x + w, bottom, 0xCC101010);
        g.renderOutline(x, y, w, bottom - y, 0xFF808080);

        int tx = x + 8;
        int maxTextW = w - 16;
        int ty = y + 8;

        g.drawString(font, title, tx, ty, 0xFFFFFF, false);
        ty += LINE_H + 4;

        g.drawString(font, Component.translatable("domains.map.claim_blocks", ClientDomainCache.usedClaimBlocks,
                ClientDomainCache.availableClaimBlocks), tx, ty, 0xAAFFAA, false);
        ty += LINE_H;
        g.drawString(font, Component.translatable("domains.map.chunkload_blocks",
                ClientDomainCache.usedChunkloadBlocks, ClientDomainCache.availableChunkloadBlocks), tx, ty, 0xFFD780,
                false);
        ty += LINE_H + 6;

        ty = divider(g, tx, ty, x + w - 8);

        if (hoverChunkX != Integer.MIN_VALUE) {
            g.drawString(font, "Chunk " + hoverChunkX + ", " + hoverChunkZ, tx, ty, 0xFFFFFF, false);
            ty += LINE_H;
            if (hoveredEntry == null) {
                g.drawString(font, Component.translatable("domains.hud.wilderness"), tx, ty, 0xAAAAAA, false);
                ty += LINE_H;
            } else {
                g.drawString(font, Component.literal(hoveredEntry.ownerName()), tx, ty, hoveredEntry.color(), false);
                ty += LINE_H;
                Component chunkloadText = Component.translatable(
                        hoveredEntry.chunkloaded() ? "domains.map.chunkloaded" : "domains.map.not_chunkloaded");
                g.drawString(font, chunkloadText, tx, ty, hoveredEntry.chunkloaded() ? 0xFFD700 : 0x888888, false);
                ty += LINE_H;
                if (isMine(hoveredEntry)) {
                    g.drawString(font, Component.translatable("domains.map.yours"), tx, ty, 0x55FF55, false);
                    ty += LINE_H;
                }
            }
        } else {
            g.drawString(font, Component.translatable("domains.map.hover_hint"), tx, ty, 0x888888, false);
            ty += LINE_H;
        }
        ty += 6;

        ty = divider(g, tx, ty, x + w - 8);

        ty = drawWrapped(g, Component.translatable("domains.map.hint_claim"), tx, ty, maxTextW);
        ty = drawWrapped(g, Component.translatable("domains.map.hint_unclaim"), tx, ty, maxTextW);
        ty = drawWrapped(g, Component.translatable("domains.map.hint_chunkload"), tx, ty, maxTextW);
        ty = drawWrapped(g, Component.translatable("domains.map.hint_pan_middle"), tx, ty, maxTextW);
        ty += 4;
        ty = divider(g, tx, ty, x + w - 8);
        renderThemeLinks(g, mx, my, tx, ty);
    }

    private int themeLinkX, themeLinkW, wikiLinkX, wikiLinkW, themeLinksY;

    private void renderThemeLinks(GuiGraphics g, int mx, int my, int x, int y) {
        themeLinksY = y;

        themeLinkX = x;
        themeLinkW = font.width("[ THEME ]");
        boolean themeHov = mx >= themeLinkX && mx < themeLinkX + themeLinkW && my >= themeLinksY &&
                my < themeLinksY + 9;
        g.drawString(font, "[ THEME ]", themeLinkX, themeLinksY,
                themeHov ? DomainsThemePalette.ACCENT : DomainsThemePalette.TEXT_DIM, false);

        wikiLinkX = themeLinkX + themeLinkW + 12;
        wikiLinkW = font.width("[ WIKI ]");
        boolean wikiHov = mx >= wikiLinkX && mx < wikiLinkX + wikiLinkW && my >= themeLinksY && my < themeLinksY + 9;
        g.drawString(font, "[ WIKI ]", wikiLinkX, themeLinksY,
                wikiHov ? DomainsThemePalette.ACCENT : DomainsThemePalette.TEXT_DIM, false);
    }

    private void openWiki() {
        if (minecraft == null) return;
        PhoenixTheme t = PhoenixTheme.current();
        WikiTheme wikiTheme = new WikiTheme(
                t.bg.getColor(), t.panel.getColor(), t.header.getColor(), t.border.getColor(),
                t.accent.getColor(), t.text.getColor(), t.textDim.getColor(), t.textFaint.getColor(),
                t.done.getColor(), t.activeColor.getColor());
        PhoenixWikiAPI.open(this, "phoenix_domains", "wiki", wikiTheme);
    }

    private int divider(GuiGraphics g, int x1, int y, int x2) {
        g.fill(x1, y, x2, y + 1, 0xFF404040);
        return y + 8;
    }

    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int maxWidth) {
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            g.drawString(font, line, x, y, 0xCCCCCC, false);
            y += LINE_H;
        }
        return y + 2;
    }

    private boolean isMine(S2CDomainSyncPacket.ClaimEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && entry.ownerName().equals(mc.player.getName().getString());
    }

    private void performClaimAction(boolean shift) {
        if (hoverChunkX == Integer.MIN_VALUE || (hoverChunkX == lastActionChunkX && hoverChunkZ == lastActionChunkZ)) {
            return;
        }
        lastActionChunkX = hoverChunkX;
        lastActionChunkZ = hoverChunkZ;
        ClaimClickActions.perform(hoverChunkX, hoverChunkZ, true, shift);
    }

    private void performUnclaimAction(boolean shift) {
        if (hoverChunkX == Integer.MIN_VALUE || (hoverChunkX == lastActionChunkX && hoverChunkZ == lastActionChunkZ)) {
            return;
        }
        lastActionChunkX = hoverChunkX;
        lastActionChunkZ = hoverChunkZ;
        ClaimClickActions.perform(hoverChunkX, hoverChunkZ, false, shift);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        if (button == 0 && mx >= themeLinkX && mx < themeLinkX + themeLinkW && my >= themeLinksY &&
                my < themeLinksY + 9) {
            if (minecraft != null) minecraft.setScreen(new PhoenixThemeEditorScreen(this, "Phoenix Domains"));
            return true;
        }
        if (button == 0 && mx >= wikiLinkX && mx < wikiLinkX + wikiLinkW && my >= themeLinksY &&
                my < themeLinksY + 9) {
            openWiki();
            return true;
        }

        if (button == 0) {
            leftHeld = true;
            lastActionChunkX = Integer.MIN_VALUE;
            lastActionChunkZ = Integer.MIN_VALUE;
            performClaimAction(hasShiftDown());
            return true;
        }
        if (button == 1) {
            rightHeld = true;
            lastActionChunkX = Integer.MIN_VALUE;
            lastActionChunkZ = Integer.MIN_VALUE;
            performUnclaimAction(hasShiftDown());
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
            pan(mx, my, dx, dy);
            return true;
        }
        if (leftHeld) {
            performClaimAction(hasShiftDown());
            return true;
        }
        if (rightHeld) {
            performUnclaimAction(hasShiftDown());
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    private void pan(double mx, double my, double dx, double dy) {
        if (gridRenderer == null) return;
        BlockPos current = gridRenderer.getBlockAtPixel(pixel(mx, my));
        BlockPos previous = gridRenderer.getBlockAtPixel(pixel(mx - dx, my - dy));
        if (current == null || previous == null) return;
        centerBlockX -= current.getX() - previous.getX();
        centerBlockZ -= current.getZ() - previous.getZ();
        recenter();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        int newZoom = (int) Math.round(Math.max(MIN_ZOOM,
                Math.min(MAX_ZOOM, zoom * (delta > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP))));
        if (newZoom != zoom && gridRenderer != null) {
            // zoom toward the cursor: keep the block under it where it is
            Point2D.Double cursor = pixel(mx, my);
            BlockPos before = gridRenderer.getBlockAtPixel(cursor);
            zoom = newZoom;
            recenter();
            BlockPos after = gridRenderer.getBlockAtPixel(cursor);
            if (before != null && after != null) {
                centerBlockX += before.getX() - after.getX();
                centerBlockZ += before.getZ() - after.getZ();
                recenter();
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
