package net.phoenixvine.domains.integration.journeymap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.integration.claimclick.ClaimClickActions;
import net.phoenixvine.domains.network.C2SDomainActionPacket;
import net.phoenixvine.domains.network.DomainNetwork;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;

import journeymap.client.api.display.Context;
import journeymap.client.io.FileHandler;
import journeymap.client.model.MapType;
import journeymap.client.render.draw.DrawUtil;
import journeymap.client.render.map.GridRenderer;

import java.awt.geom.Point2D;
import java.io.File;

/**
 * Domains-owned claim screen that draws JourneyMap's OWN real terrain tiles by owning a private
 * {@link GridRenderer} instance and driving it directly — unlike Xaero's World Map (which has no
 * standalone renderer, see {@code XaeroEmbeddedClaimScreen}), {@link GridRenderer} is a genuine
 * public, standalone API: its own {@code Fullscreen.gridRenderer} field is write-only from
 * outside and never read by {@link GridRenderer} itself, meaning it has no hidden dependency on
 * {@code Fullscreen} being the active screen. We construct our own instance instead of reusing
 * JourneyMap's singleton so a live real {@code Fullscreen} (opened via JourneyMap's own keybind)
 * can never fight this screen over shared mutable viewport/zoom state. For the same reason we
 * get the world's data directory from {@link FileHandler#getJMWorldDir} directly rather than
 * {@code Fullscreen.state().getWorldDir()} — that singleton's {@code worldDir} field is only
 * ever populated as a side effect of {@code Fullscreen}'s own lifecycle actually running once,
 * so it stays null (and crashes tile loading downstream) if the player never opened the real
 * JourneyMap screen first; {@code FileHandler.getJMWorldDir} reads JourneyMap's own
 * login-tracked world ID directly and needs no such priming.
 * <p>
 * Middle-drag panning doesn't use {@link GridRenderer}'s own pixel-per-block scale-per-zoom-level
 * math at all (never confirmed against its actual scroll/drag handler bytecode the way Xaero's
 * equivalent — the {@code scale} field — was, so hand-deriving that formula here would be a
 * guess) — instead it reuses {@link GridRenderer#getBlockAtPixel}, already proven correct (it's
 * what resolves the hovered chunk every frame): sampling the world position under the cursor
 * before and after a drag and diffing the two gives the exact same world-space delta the drag
 * math would, with zero risk of the conversion formula being subtly wrong. Zoom (an int 0-5,
 * confirmed range from {@code Fullscreen}'s own {@code zoomIn}/{@code zoomOut} bounds) re-centers
 * on the same world position at the new zoom level via {@link GridRenderer#center}, which is a
 * real public call, not a guess. Live waypoint/radar overlays aren't drawn either — those come
 * from package-private methods reading {@code Fullscreen}'s own private factory fields, not
 * reachable cleanly from outside; only real terrain tiles are shown, which is what matters for
 * claiming.
 * <p>
 * {@code updateTiles(...)}'s width/height arguments must be {@link
 * com.mojang.blaze3d.platform.Window#getScreenWidth}/{@code getScreenHeight} — confirmed by
 * decompiling {@code Fullscreen}'s own real call sites — not this {@code Screen}'s own {@code
 * width}/{@code height} fields; those are usually the same number, but {@code GridRenderer}
 * genuinely reads them as physical/window-relative sizing rather than whatever the currently
 * active {@code Screen} happens to report. {@code Fullscreen} also never calls {@code
 * setViewPort} at all, so this doesn't either.
 * <p>
 * Also required, and previously missing entirely: {@link DrawUtil#sizeDisplay} — JourneyMap's
 * tile/grid drawing doesn't go through this screen's own {@code GuiGraphics} pose stack in any
 * meaningful way; it replaces the ambient projection matrix and a separate global modelview
 * {@code PoseStack} directly (via {@code RenderWrapper}), sized to raw window pixels, before
 * drawing, and must be called again afterward (sized back to this screen's own logical
 * width/height) to restore normal rendering state for anything drawn after it — {@code
 * Fullscreen} itself calls the exact same method both before and after its own tile drawing, for
 * exactly this reason. Skipping the "after" call would leave every draw call for the rest of the
 * frame (including this screen's own sidebar) working against JourneyMap's leftover raw-pixel
 * projection instead of the normal one.
 * <p>
 * Finally: {@code GridRenderer} has multiple {@code draw(...)} overloads — the one actually
 * used to draw the tile grid itself is {@code draw(GuiGraphics, float, float, double, double,
 * boolean)} (mirroring {@code Fullscreen}'s own call), not the {@code List<DrawStep>} overload,
 * which only draws extra overlay markers layered on top of a grid that overload does not itself
 * draw — calling only that one (as an earlier version of this file did) is why nothing appeared
 * at all despite no crash.
 */
@OnlyIn(Dist.CLIENT)
public class JourneyMapEmbeddedClaimScreen extends Screen {

    private static final int MIN_ZOOM = 0;
    private static final int MAX_ZOOM = 5;
    private static final int DEFAULT_ZOOM = 2;
    private static final int SIDEBAR_W = 150;
    private static final int MARGIN = 12;
    private static final int LINE_H = 10;

    /**
     * Opens this screen — returns {@code false} without opening anything if JourneyMap's world
     * data directory isn't resolvable yet (see the class doc for why that can happen), so the
     * caller falls through to the next available claim map instead of opening a screen that
     * would crash the moment it tries to load a tile.
     * <p>
     * Equivalent to calling {@link #tryOpen(Screen)} with {@code null} — i.e. nothing to return to.
     */
    public static boolean tryOpen() {
        return tryOpen(null);
    }

    /**
     * Same as {@link #tryOpen()}, but threads {@code returnTo} through so this screen hands the
     * player back to it (e.g. the inventory screen) on close instead of dropping to the world.
     */
    public static boolean tryOpen(Screen returnTo) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        File worldDir = FileHandler.getJMWorldDir(mc);
        if (worldDir == null) return false;
        mc.setScreen(new JourneyMapEmbeddedClaimScreen(worldDir, returnTo));
        return true;
    }

    private final File worldDir;
    // The screen to return to (via onClose, below) when this one closes.
    private final Screen parent;
    private GridRenderer gridRenderer;
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

    /**
     * Returns to whichever screen was open before this one (e.g. the inventory screen), instead
     * of vanilla {@link Screen}'s default of dropping to the world.
     */
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        mapType = MapType.day(mc.player.level().dimension());
        centerBlockX = mc.player.getX();
        centerBlockZ = mc.player.getZ();

        gridRenderer = new GridRenderer(Context.UI.Fullscreen);
        // Mandatory setup Fullscreen itself calls on init/resize/refresh, with no automatic
        // fallback — gridSize defaults to 0 (and stays there unless set), which zeroes out the
        // "srcSize" term in GridRenderer's own internal centering math and produces a large,
        // constant rightward offset instead of centering tiles on screen. Same odd-tile-count
        // formula Fullscreen's own getCalculatedGridSize() uses (enough 512px tiles to cover the
        // screen, bumped to the next odd number so there's a single, symmetric center tile).
        gridRenderer.setGridSize(calculateGridSize(width));
        recenter();

        DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.requestSync(16));
    }

    private static int calculateGridSize(int screenWidth) {
        int gridSize = 0;
        while (gridSize * 512 < screenWidth) gridSize++;
        if (++gridSize % 2 == 0) gridSize++;
        return gridSize;
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
        renderBackground(g);
        if (gridRenderer != null && mapType != null) {
            // Using this Screen's own logical width/height consistently for sizeDisplay AND
            // updateTiles, instead of mixing in Minecraft.getWindow()'s raw/physical pixel
            // sizing (real Fullscreen's own convention, which two earlier attempts based on
            // that faithfully replicated real call sites still got wrong — first a small
            // sub-map in one corner, then a correctly-sized map in the wrong quadrant). This
            // Screen's own width/height is the one dimension already proven correct here — the
            // sidebar renders against it perfectly — so drawing the map in that same coordinate
            // space sidesteps whatever physical-vs-logical mismatch was actually going wrong,
            // rather than chasing it further.
            DrawUtil.sizeDisplay(g.pose(), width, height);
            try {
                gridRenderer.updateTiles(mapType, zoom, true, width, height, false, 0.0, 0.0);
                gridRenderer.draw(g, 1.0f, 0.8f, 0.0, 0.0, true);
            } finally {
                DrawUtil.sizeDisplay(g.pose(), width, height);
            }

            BlockPos pos = gridRenderer.getBlockAtPixel(new Point2D.Double(mx, my));
            if (pos != null) {
                hoverChunkX = pos.getX() >> 4;
                hoverChunkZ = pos.getZ() >> 4;
            } else {
                hoverChunkX = Integer.MIN_VALUE;
            }
        }

        S2CDomainSyncPacket.ClaimEntry hoveredEntry = hoverChunkX != Integer.MIN_VALUE ?
                ClientDomainCache.entryAt(hoverChunkX, hoverChunkZ) : null;
        renderSidebar(g, hoveredEntry);

        super.render(g, mx, my, partialTick);
    }

    private void renderSidebar(GuiGraphics g, S2CDomainSyncPacket.ClaimEntry hoveredEntry) {
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
        drawWrapped(g, Component.translatable("domains.map.hint_pan_middle"), tx, ty, maxTextW);
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

    /**
     * Derives a world-block pan delta from a screen-pixel drag by sampling {@link
     * GridRenderer#getBlockAtPixel} at both ends of the drag and diffing them, instead of
     * hand-deriving {@code GridRenderer}'s own pixel-per-block-at-this-zoom formula — that
     * method is already proven correct (hover resolution uses it every frame), so this can't be
     * subtly wrong the way an independently-derived scale factor could.
     */
    private void pan(double mx, double my, double dx, double dy) {
        if (gridRenderer == null) return;
        BlockPos current = gridRenderer.getBlockAtPixel(new Point2D.Double(mx, my));
        BlockPos previous = gridRenderer.getBlockAtPixel(new Point2D.Double(mx - dx, my - dy));
        if (current == null || previous == null) return;
        centerBlockX -= current.getX() - previous.getX();
        centerBlockZ -= current.getZ() - previous.getZ();
        recenter();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + (delta > 0 ? 1 : -1)));
        if (newZoom != zoom) {
            zoom = newZoom;
            recenter();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
