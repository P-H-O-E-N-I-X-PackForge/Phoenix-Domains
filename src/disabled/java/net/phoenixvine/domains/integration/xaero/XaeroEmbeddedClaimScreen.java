package net.phoenixvine.domains.integration.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.client.DomainsThemePalette;
import net.phoenixvine.domains.integration.claimclick.ClaimClickActions;
import net.phoenixvine.domains.network.C2SDomainActionPacket;
import net.phoenixvine.domains.network.DomainNetwork;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;
import net.phoenixvine.wiki.PhoenixWikiAPI;
import net.phoenixvine.wiki.client.screen.WikiTheme;
import net.phoenixvine.wiki.theme.PhoenixTheme;
import net.phoenixvine.wiki.theme.PhoenixThemeEditorScreen;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.gui.GuiMap;

import java.lang.reflect.Field;

@OnlyIn(Dist.CLIENT)
public class XaeroEmbeddedClaimScreen extends Screen {

    private static final double MIN_USER_SCALE = 0.05;
    private static final double MAX_USER_SCALE = 64.0;
    private static final double ZOOM_STEP = 1.15;
    private static final int SIDEBAR_W = 150;
    private static final int MARGIN = 12;
    private static final int LINE_H = 10;

    private static Field cameraXField;
    private static Field cameraZField;
    private static Field scaleField;
    private static Field userScaleField;
    private static Field mouseBlockPosXField;
    private static Field mouseBlockPosZField;
    private static Field mouseBlockDimField;
    private static Field screenWidthField;
    private static Field screenHeightField;
    private static Field minecraftField;
    private static Field fontField;
    private static Field itemRendererField;
    private static boolean fieldsResolved;

    static {
        try {
            cameraXField = GuiMap.class.getDeclaredField("cameraX");
            cameraXField.setAccessible(true);
            cameraZField = GuiMap.class.getDeclaredField("cameraZ");
            cameraZField.setAccessible(true);
            scaleField = GuiMap.class.getDeclaredField("scale");
            scaleField.setAccessible(true);
            userScaleField = GuiMap.class.getDeclaredField("userScale");
            userScaleField.setAccessible(true);
            mouseBlockPosXField = GuiMap.class.getDeclaredField("mouseBlockPosX");
            mouseBlockPosXField.setAccessible(true);
            mouseBlockPosZField = GuiMap.class.getDeclaredField("mouseBlockPosZ");
            mouseBlockPosZField.setAccessible(true);
            mouseBlockDimField = GuiMap.class.getDeclaredField("mouseBlockDim");
            mouseBlockDimField.setAccessible(true);
            screenWidthField = Screen.class.getDeclaredField("width");
            screenWidthField.setAccessible(true);
            screenHeightField = Screen.class.getDeclaredField("height");
            screenHeightField.setAccessible(true);
            minecraftField = Screen.class.getDeclaredField("minecraft");
            minecraftField.setAccessible(true);
            fontField = Screen.class.getDeclaredField("font");
            fontField.setAccessible(true);
            itemRendererField = Screen.class.getDeclaredField("itemRenderer");
            itemRendererField.setAccessible(true);
            fieldsResolved = true;
        } catch (ReflectiveOperationException e) {
            PhoenixDomains.LOGGER.error("Xaero's World Map is present but the fields this embedded claim map" +
                    " needs to puppeteer GuiMap couldn't be found (Xaero update changed its internals?) —" +
                    " disabled.", e);
            fieldsResolved = false;
        }
    }

    public static boolean isUsable() {
        return fieldsResolved;
    }

    public static boolean tryOpen() {
        return tryOpen(null);
    }

    public static boolean tryOpen(Screen returnTo) {
        if (!fieldsResolved) return false;
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || !session.isUsable()) return false;
        Minecraft.getInstance().setScreen(new XaeroEmbeddedClaimScreen(session.getMapProcessor(), returnTo));
        return true;
    }

    private final MapProcessor mapProcessor;

    private final Screen parent;
    private GuiMap guiMap;
    private boolean panning = false;
    private boolean leftHeld = false;
    private boolean rightHeld = false;
    private int lastActionChunkX = Integer.MIN_VALUE;
    private int lastActionChunkZ = Integer.MIN_VALUE;
    private int hoverChunkX = Integer.MIN_VALUE;
    private int hoverChunkZ;
    private boolean loggedDiagnostics = false;

    private XaeroEmbeddedClaimScreen(MapProcessor mapProcessor, Screen parent) {
        super(Component.translatable("domains.map.title"));
        this.mapProcessor = mapProcessor;
        this.parent = parent;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();

        guiMap = new GuiMap(null, null, mapProcessor, mc.player);
        try {
            minecraftField.set(guiMap, mc);
            fontField.set(guiMap, mc.font);
            itemRendererField.set(guiMap, mc.getItemRenderer());
            screenWidthField.set(guiMap, this.width);
            screenHeightField.set(guiMap, this.height);
        } catch (ReflectiveOperationException e) {
            PhoenixDomains.LOGGER.error("Failed to initialize the puppeteered GuiMap instance — the embedded" +
                    " Xaero claim map may render incorrectly.", e);
        }

        DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.requestSync(mapProcessor == null ? 8 : 16));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        if (guiMap != null) {

            if (!loggedDiagnostics) {
                logDiagnostics();
                loggedDiagnostics = true;
            }
            guiMap.render(g, mx, my, partialTick);
            readHoveredChunk();
        }

        S2CDomainSyncPacket.ClaimEntry hoveredEntry = hoverChunkX != Integer.MIN_VALUE ?
                ClientDomainCache.entryAt(hoverChunkX, hoverChunkZ) : null;
        renderSidebar(g, mx, my, hoveredEntry);

        super.render(g, mx, my, partialTick);
    }

    private void logDiagnostics() {
        try {
            var window = Minecraft.getInstance().getWindow();
            int fboBeforeRender = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            double cameraX = (double) cameraXField.get(guiMap);
            double cameraZ = (double) cameraZField.get(guiMap);
            double scale = (double) scaleField.get(guiMap);
            double userScale = (double) userScaleField.get(guiMap);
            PhoenixDomains.LOGGER.info(
                    "[Domains] Xaero embedded map diagnostics: guiScale={} window={}x{}" +
                            " (physical) screenGuiScaled={}x{} thisWidthHeight={}x{} cameraX={} cameraZ={} scale={}" +
                            " userScale={} boundFboBeforeRender={}",
                    window.getGuiScale(), window.getWidth(),
                    window.getHeight(), window.getGuiScaledWidth(), window.getGuiScaledHeight(), this.width,
                    this.height, cameraX, cameraZ, scale, userScale, fboBeforeRender);
        } catch (Throwable t) {
            PhoenixDomains.LOGGER.error("[Domains] Xaero embedded map diagnostics failed to gather.", t);
        }
    }

    private void readHoveredChunk() {
        try {
            @SuppressWarnings("unchecked")
            ResourceKey<Level> dim = (ResourceKey<Level>) mouseBlockDimField.get(guiMap);
            if (dim == null) {
                hoverChunkX = Integer.MIN_VALUE;
                return;
            }
            hoverChunkX = ((int) mouseBlockPosXField.get(guiMap)) >> 4;
            hoverChunkZ = ((int) mouseBlockPosZField.get(guiMap)) >> 4;
        } catch (ReflectiveOperationException e) {
            hoverChunkX = Integer.MIN_VALUE;
        }
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

    private void pan(double dx, double dz) {
        try {
            double scale = (double) scaleField.get(guiMap);
            if (scale <= 0) return;
            double cx = (double) cameraXField.get(guiMap);
            double cz = (double) cameraZField.get(guiMap);
            cameraXField.set(guiMap, cx - dx / scale);
            cameraZField.set(guiMap, cz - dz / scale);
        } catch (ReflectiveOperationException ignored) {

        }
    }

    private void zoom(double delta) {
        try {
            double userScale = (double) userScaleField.get(guiMap);
            double factor = delta > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP;
            double clamped = Math.max(MIN_USER_SCALE, Math.min(MAX_USER_SCALE, userScale * factor));
            userScaleField.set(guiMap, clamped);
        } catch (ReflectiveOperationException ignored) {

        }
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
            pan(dx, dy);
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

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        zoom(delta);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
