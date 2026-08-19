package net.phoenixvine.domains.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.client.DomainsThemePalette;
import net.phoenixvine.domains.network.C2SDomainActionPacket;
import net.phoenixvine.domains.network.DomainNetwork;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;
import net.phoenixvine.wiki.PhoenixWikiAPI;
import net.phoenixvine.wiki.client.screen.WikiTheme;
import net.phoenixvine.wiki.theme.PhoenixTheme;
import net.phoenixvine.wiki.theme.PhoenixThemeEditorScreen;

import java.util.HashMap;
import java.util.Map;

/**
 * Claim grid: a top-down chunk grid centered on the player. Deliberately grid-based
 * rather than a pannable/zoomable terrain view — claiming is an inherently per-chunk
 * action, so a literal chunk grid with a side info panel reads better than scrolling
 * around Solaris's full map to poke at one square. Solaris is only a soft/optional
 * dependency of this mod (its claim-tint overlay lives in {@code integration.solaris},
 * applied to Solaris's own map screen when present) — this screen is always what
 * Domains' own map keybind opens, so it must render with vanilla-only primitives and
 * never reach for Solaris chrome.
 * <p>
 * Each cell's base color is one real terrain sample (the top non-air block's vanilla
 * {@link MapColor} at that chunk's center column, the same per-block color vanilla's own
 * item maps use) rather than a flat gray — enough to actually read as a map (forests,
 * water, sand, stone) without the cost of Solaris's full per-pixel terrain pipeline, which
 * isn't available here since this screen is exactly what renders when Solaris isn't
 * installed. One sample per chunk, cached forever once a chunk is loaded (this screen's
 * center never moves after {@link #init()}, so terrain never needs to be re-sampled) —
 * chunks not yet loaded just show a neutral placeholder and get sampled lazily the moment
 * they become available.
 * <p>
 * The grid's radius is capped to the client's own render distance setting (minus a small
 * margin), not a fixed constant — a fixed radius bigger than what the client actually keeps
 * loaded around the player would show real terrain only near the center and a permanently
 * gray, never-populated ring around the rest of the grid, which reads as "stopped loading"
 * even though it's working exactly as designed (unloaded chunks are retried every frame,
 * they just never succeed if they're outside the player's own loaded/simulated area).
 *
 * Controls: left-click claims an unclaimed chunk, right-click unclaims your own — separate
 * dedicated buttons rather than one toggle, specifically so you can hold the button down and
 * drag across many chunks to mass-claim/unclaim without worrying about a chunk you're just
 * passing over toggling to the opposite of what you want. Shift+left-click claims AND
 * chunkloads a chunk in one action (or just turns chunkload on if you already own it);
 * shift+right-click turns chunkload off without unclaiming. The server is the actual
 * authority on all of this (ownership/permission/power checks, plus a max-claim-distance
 * limit) — clicks just fire the same {@code C2SDomainActionPacket} actions it already
 * validates.
 */
@OnlyIn(Dist.CLIENT)
public class ClaimMapScreen extends Screen {

    private static final int MAX_RADIUS = 16;
    private static final int MIN_RADIUS = 4;
    private static final int MIN_CELL = 10;
    private static final int SIDEBAR_W = 160;
    private static final int MARGIN = 12;
    private static final int LINE_H = 10;
    private static final int UNLOADED_COLOR = 0xFF303030;

    private static final int GRID_LINE = 0x1EFFFFFF;
    private static final int CHUNKLOADED_RGB = 0xFFD700;
    private static final int YOURS_RGB = 0x55FF55;
    private static final int WATER_RGB = 0x2D5DA6;

    private final Map<Long, Integer> terrainColorCache = new HashMap<>();

    // The screen to return to (via onClose, below) when this one closes - e.g. the inventory
    // screen if this was opened from the cross-suite HUD bar button while inventory was open, or
    // null if there was nothing open beforehand (opened from plain gameplay via keybind).
    private final Screen parent;

    private int radius;
    private int centerChunkX = Integer.MIN_VALUE;
    private int centerChunkZ = Integer.MIN_VALUE;
    private int cell;
    private int gridPx;
    private int gridPy;
    private int gridSize;

    private int hoveredX = Integer.MIN_VALUE;
    private int hoveredZ;

    private boolean leftHeld = false;
    private boolean rightHeld = false;
    private int lastActionChunkX = Integer.MIN_VALUE;
    private int lastActionChunkZ = Integer.MIN_VALUE;

    public ClaimMapScreen() {
        this(null);
    }

    public ClaimMapScreen(Screen parent) {
        super(Component.translatable("domains.map.title"));
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

        // Undershoot the client's own render distance setting by a couple of chunks — actually
        // *loaded*/simulated chunks tend to lag slightly behind that setting (it's a target, not
        // a guarantee), so matching it exactly would still leave a thin gray ring at the edge.
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, mc.options.renderDistance().get() - 2));

        int span = radius * 2 + 1;
        int available = width - SIDEBAR_W - MARGIN * 3;
        gridSize = Math.min(available, height - MARGIN * 2);
        cell = Math.max(MIN_CELL, gridSize / span);
        gridSize = cell * span;
        gridPx = MARGIN;
        gridPy = (height - gridSize) / 2;

        recenterOnPlayer();
    }

    /**
     * Re-reads the player's current chunk position — called every {@link #render}, not just
     * once at {@link #init()}, since this screen isn't a pause screen and the player can walk
     * around while it's open. A one-time snapshot centered on wherever the player happened to be
     * when the map was opened would otherwise stay put while they moved away from it, which
     * reads as "the map doesn't follow me."
     */
    private void recenterOnPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int cx = mc.player.blockPosition().getX() >> 4;
        int cz = mc.player.blockPosition().getZ() >> 4;
        if (cx == centerChunkX && cz == centerChunkZ) return;
        centerChunkX = cx;
        centerChunkZ = cz;
        DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.requestSync(radius));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        DomainsThemePalette.refresh();
        recenterOnPlayer();
        renderBackground(g);
        panel(g, gridPx - 4, gridPy - 4, gridSize + 8, gridSize + 8);
        renderThemeLinks(g, mx, my);

        Level level = Minecraft.getInstance().level;

        hoveredX = Integer.MIN_VALUE;
        S2CDomainSyncPacket.ClaimEntry hoveredEntry = null;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = centerChunkX + dx;
                int cz = centerChunkZ + dz;
                int x0 = gridPx + (dx + radius) * cell;
                int y0 = gridPy + (dz + radius) * cell;

                g.fill(x0, y0, x0 + cell, y0 + cell, terrainColorAt(level, cx, cz));
                g.renderOutline(x0, y0, cell, cell, GRID_LINE);

                S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(cx, cz);
                if (entry != null) {
                    // Chunkload is a property of an existing claim, not an independent state — a
                    // chunkloaded claim is solid gold rather than owner-color-plus-a-strip, so
                    // "claimed" and "claimed + chunkloaded" read as genuinely distinct at a
                    // glance. A light tint plus a solid owner-colored border (thicker for
                    // chunkloaded claims) reads as "a plot on a map" — mirroring how claim mods
                    // like FTB Chunks mark ownership — rather than an opaque block of color
                    // that hides the terrain sampled underneath it.
                    int rgb = entry.chunkloaded() ? CHUNKLOADED_RGB : (entry.color() & 0xFFFFFF);
                    g.fill(x0 + 1, y0 + 1, x0 + cell - 1, y0 + cell - 1, 0x50000000 | rgb);
                    outlineRect(g, x0, y0, cell, cell, 0xFF000000 | rgb, entry.chunkloaded() ? 2 : 1);
                }

                if (dx == 0 && dz == 0) {
                    outlineRect(g, x0, y0, cell, cell, 0xFFFFFFFF, 2);
                }

                if (mx >= x0 && mx < x0 + cell && my >= y0 && my < y0 + cell) {
                    hoveredX = cx;
                    hoveredZ = cz;
                    hoveredEntry = entry;
                    g.fill(x0, y0, x0 + cell, y0 + cell, 0x40FFFFFF);
                    outlineRect(g, x0, y0, cell, cell, 0xAAFFFFFF, 1);
                }
            }
        }

        renderSidebar(g, hoveredEntry);

        super.render(g, mx, my, partialTick);
    }

    /** A dark, gently gradient-shaded panel with a subtle bevel border — the shared "chrome" for both panels. */
    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, DomainsThemePalette.PANEL_BG_TOP, DomainsThemePalette.PANEL_BG_BOTTOM);
        g.renderOutline(x, y, w, h, DomainsThemePalette.PANEL_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, DomainsThemePalette.PANEL_HILITE);
    }

    private int themeLinkX, themeLinkW, wikiLinkX, wikiLinkW, themeLinksY;

    /** A small "Theme" / "Wiki" link pair in the top-left corner, above the grid panel. */
    private void renderThemeLinks(GuiGraphics g, int mx, int my) {
        themeLinksY = MARGIN - 10;
        if (themeLinksY < 2) themeLinksY = 2;

        themeLinkX = gridPx;
        themeLinkW = font.width("✎ Theme");
        boolean themeHov = mx >= themeLinkX && mx < themeLinkX + themeLinkW && my >= themeLinksY &&
                my < themeLinksY + 9;
        g.drawString(font, themeHov ? "§b✎ Theme" : "§8✎ Theme", themeLinkX, themeLinksY,
                DomainsThemePalette.TEXT_DIM, false);

        wikiLinkX = themeLinkX + themeLinkW + 12;
        wikiLinkW = font.width("📖 Wiki");
        boolean wikiHov = mx >= wikiLinkX && mx < wikiLinkX + wikiLinkW && my >= themeLinksY && my < themeLinksY + 9;
        g.drawString(font, wikiHov ? "§b📖 Wiki" : "§8📖 Wiki", wikiLinkX, themeLinksY,
                DomainsThemePalette.TEXT_DIM, false);
    }

    /**
     * Opens the dev wiki via the jar-in-jar'd Phoenix Wiki library, themed to match this mod's
     * currently active (suite-wide shared) PhoenixTheme.
     */
    private void openWiki() {
        if (minecraft == null) return;
        PhoenixTheme t = PhoenixTheme.current();
        WikiTheme wikiTheme = new WikiTheme(
                t.bg.getColor(), t.panel.getColor(), t.header.getColor(), t.border.getColor(),
                t.accent.getColor(), t.text.getColor(), t.textDim.getColor(), t.textFaint.getColor(),
                t.done.getColor(), t.activeColor.getColor());
        PhoenixWikiAPI.open(this, "phoenix_domains", "wiki", wikiTheme);
    }

    private void outlineRect(GuiGraphics g, int x, int y, int w, int h, int color, int thickness) {
        g.fill(x, y, x + w, y + thickness, color);
        g.fill(x, y + h - thickness, x + w, y + h, color);
        g.fill(x, y, x + thickness, y + h, color);
        g.fill(x + w - thickness, y, x + w, y + h, color);
    }

    /**
     * The top non-air block's vanilla map color at this chunk's center column, cached forever
     * once sampled (this screen's grid never re-centers, so terrain never changes underneath
     * it). Chunks not yet loaded return a neutral placeholder without being cached, so they get
     * a real sample the moment they do load in.
     */
    private int terrainColorAt(Level level, int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        Integer cached = terrainColorCache.get(key);
        if (cached != null) return cached;
        if (level == null || !level.hasChunk(chunkX, chunkZ)) return UNLOADED_COLOR;

        int blockX = (chunkX << 4) + 8;
        int blockZ = (chunkZ << 4) + 8;
        BlockPos pos = new BlockPos(blockX, level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) - 1,
                blockZ);
        BlockState state = level.getBlockState(pos);

        int rgb;
        if (!state.getFluidState().isEmpty() && state.getFluidState().is(FluidTags.WATER)) {
            // Vanilla's own MapColor.WATER is the same flat, washed-out light blue every Java
            // Edition item map uses — fine at map-item scale, but flat and a bit ugly filling an
            // entire chunk cell here. A richer, slightly deeper blue reads better at this size
            // without pretending to be a real depth-shaded water renderer.
            rgb = WATER_RGB;
        } else {
            // MapColor#calculateRGBColor packs its result as `-16777216 | blue<<16 | green<<8 |
            // red` (meant for NativeImage's byte order), not standard ARGB — used directly, that
            // silently swaps red and blue for every terrain color, which is exactly what turned
            // water (correctly detected or not) reddish instead of blue. Un-swap it back to
            // proper ARGB before the small flat darken (which itself just reads a touch
            // richer/moodier than vanilla's fairly washed-out map palette — subtle on purpose,
            // this is still meant to be a plain, cheap fallback, not Solaris's full terrain
            // pipeline).
            int packed = state.getMapColor(level, pos).calculateRGBColor(MapColor.Brightness.NORMAL);
            int properRgb = ((packed & 0xFF) << 16) | (packed & 0x00FF00) | ((packed >> 16) & 0xFF);
            rgb = darken(properRgb, 0.9f);
        }

        int color = 0xFF000000 | rgb;
        terrainColorCache.put(key, color);
        return color;
    }

    private static int darken(int rgb, float factor) {
        int r = (int) (((rgb >> 16) & 0xFF) * factor);
        int gr = (int) (((rgb >> 8) & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return (r << 16) | (gr << 8) | b;
    }

    private void renderSidebar(GuiGraphics g, S2CDomainSyncPacket.ClaimEntry hoveredEntry) {
        int x = width - SIDEBAR_W - MARGIN;
        int y = MARGIN;
        int w = SIDEBAR_W;
        int bottom = height - MARGIN;

        panel(g, x, y, w, bottom - y);

        int tx = x + 8;
        int maxTextW = w - 16;
        int ty = y + 8;

        g.drawString(font, title, tx, ty, DomainsThemePalette.TEXT, false);
        ty += LINE_H + 2;
        g.fill(tx, ty, x + w - 8, ty + 1, DomainsThemePalette.ACCENT);
        ty += 6;

        g.drawString(font, Component.translatable("domains.map.claim_blocks", ClientDomainCache.usedClaimBlocks,
                ClientDomainCache.availableClaimBlocks), tx, ty, 0xAAFFAA, false);
        ty += LINE_H;
        g.drawString(font, Component.translatable("domains.map.chunkload_blocks",
                ClientDomainCache.usedChunkloadBlocks, ClientDomainCache.availableChunkloadBlocks), tx, ty, 0xFFD780,
                false);
        ty += LINE_H + 6;

        ty = divider(g, tx, ty, x + w - 8);

        if (hoveredX != Integer.MIN_VALUE) {
            g.drawString(font, "Chunk " + hoveredX + ", " + hoveredZ, tx, ty, DomainsThemePalette.TEXT, false);
            ty += LINE_H;
            if (hoveredEntry == null) {
                g.drawString(font, Component.translatable("domains.hud.wilderness"), tx, ty,
                        DomainsThemePalette.TEXT_DIM, false);
                ty += LINE_H;
            } else {
                g.drawString(font, Component.literal(hoveredEntry.ownerName()), tx, ty, hoveredEntry.color(), false);
                ty += LINE_H;
                Component chunkloadText = Component.translatable(
                        hoveredEntry.chunkloaded() ? "domains.map.chunkloaded" : "domains.map.not_chunkloaded");
                g.drawString(font, chunkloadText, tx, ty,
                        hoveredEntry.chunkloaded() ? CHUNKLOADED_RGB : DomainsThemePalette.TEXT_FAINT, false);
                ty += LINE_H;
                if (isMine(hoveredEntry)) {
                    g.drawString(font, Component.translatable("domains.map.yours"), tx, ty, YOURS_RGB, false);
                    ty += LINE_H;
                }
            }
        } else {
            g.drawString(font, Component.translatable("domains.map.hover_hint"), tx, ty,
                    DomainsThemePalette.TEXT_FAINT, false);
            ty += LINE_H;
        }
        ty += 6;

        ty = divider(g, tx, ty, x + w - 8);

        ty = legendRow(g, tx, ty, YOURS_RGB, "domains.map.legend_yours");
        ty = legendRow(g, tx, ty, CHUNKLOADED_RGB, "domains.map.legend_chunkloaded");
        ty += 4;

        ty = divider(g, tx, ty, x + w - 8);

        ty = drawWrapped(g, Component.translatable("domains.map.hint_claim"), tx, ty, maxTextW);
        ty = drawWrapped(g, Component.translatable("domains.map.hint_unclaim"), tx, ty, maxTextW);
        drawWrapped(g, Component.translatable("domains.map.hint_chunkload"), tx, ty, maxTextW);
    }

    /** A small colored swatch followed by a label — used for the ownership legend. */
    private int legendRow(GuiGraphics g, int x, int y, int swatchColor, String labelKey) {
        int swatch = 7;
        int textY = y + (swatch - font.lineHeight) / 2;
        g.fill(x, y, x + swatch, y + swatch, 0xFF000000 | swatchColor);
        g.renderOutline(x, y, swatch, swatch, 0x80000000);
        g.drawString(font, Component.translatable(labelKey), x + swatch + 5, textY, DomainsThemePalette.TEXT_DIM,
                false);
        return y + LINE_H + 2;
    }

    private int divider(GuiGraphics g, int x1, int y, int x2) {
        g.fill(x1, y, x2, y + 1, 0xFF050505);
        g.fill(x1, y + 1, x2, y + 2, 0x18FFFFFF);
        return y + 9;
    }

    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int maxWidth) {
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            g.drawString(font, line, x, y, DomainsThemePalette.TEXT_DIM, false);
            y += LINE_H;
        }
        return y + 2;
    }

    private boolean isMine(S2CDomainSyncPacket.ClaimEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && entry.ownerName().equals(mc.player.getName().getString());
    }

    /**
     * Claims (or shift: claims+chunkloads / turns chunkload on) whatever's currently hovered
     * — no-ops if we already acted on this exact chunk since the button went down, so holding
     * the button and dragging across many chunks claims each one once, not every frame.
     */
    private void performClaimAction(boolean shift) {
        if (hoveredX == Integer.MIN_VALUE || (hoveredX == lastActionChunkX && hoveredZ == lastActionChunkZ)) return;
        lastActionChunkX = hoveredX;
        lastActionChunkZ = hoveredZ;

        S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(hoveredX, hoveredZ);
        if (shift) {
            if (entry == null) {
                DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.claim(hoveredX, hoveredZ));
                DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.setChunkloaded(hoveredX, hoveredZ, true));
            } else {
                DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.setChunkloaded(hoveredX, hoveredZ, true));
            }
        } else if (entry == null) {
            DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.claim(hoveredX, hoveredZ));
        }
    }

    /**
     * Unclaims (or shift: just removes chunkload, keeping the claim) whatever's hovered — same
     * once-per-chunk-per-drag guard as {@link #performClaimAction}.
     */
    private void performUnclaimAction(boolean shift) {
        if (hoveredX == Integer.MIN_VALUE || (hoveredX == lastActionChunkX && hoveredZ == lastActionChunkZ)) return;
        lastActionChunkX = hoveredX;
        lastActionChunkZ = hoveredZ;

        S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(hoveredX, hoveredZ);
        if (entry == null) return;
        if (shift) {
            DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.setChunkloaded(hoveredX, hoveredZ, false));
        } else {
            DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.unclaim(hoveredX, hoveredZ));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= themeLinkX && mx < themeLinkX + themeLinkW && my >= themeLinksY &&
                my < themeLinksY + 9) {
            if (minecraft != null) minecraft.setScreen(new PhoenixThemeEditorScreen(this, "Phoenix Domains"));
            return true;
        }
        if (button == 0 && mx >= wikiLinkX && mx < wikiLinkX + wikiLinkW && my >= themeLinksY && my < themeLinksY + 9) {
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
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
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

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) leftHeld = false;
        if (button == 1) rightHeld = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
