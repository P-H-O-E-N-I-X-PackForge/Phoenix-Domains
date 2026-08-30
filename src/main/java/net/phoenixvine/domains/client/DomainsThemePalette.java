package net.phoenixvine.domains.client;

import net.phoenixvine.wiki.theme.PhoenixTheme;

public final class DomainsThemePalette {

    private DomainsThemePalette() {}

    public static int PANEL_BG_TOP, PANEL_BG_BOTTOM, PANEL_BORDER, PANEL_HILITE, ACCENT;
    public static int TEXT, TEXT_DIM, TEXT_FAINT;

    public static void refresh() {
        PhoenixTheme t = PhoenixTheme.current();

        PANEL_BG_TOP = withAlpha(t.panel.getColor(), 0xE6);
        PANEL_BG_BOTTOM = withAlpha(t.bg.getColor(), 0xE6);
        PANEL_BORDER = t.border.getColor();
        PANEL_HILITE = 0x22FFFFFF;
        ACCENT = t.accent.getColor();

        TEXT = t.text.getColor();
        TEXT_DIM = t.textDim.getColor();
        TEXT_FAINT = t.textFaint.getColor();
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
