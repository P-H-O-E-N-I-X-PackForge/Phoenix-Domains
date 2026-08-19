package net.phoenixvine.domains.mixin;

import net.minecraftforge.fml.loading.FMLLoader;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * {@code phoenix_domains.mixins.json} has {@code "required": true} at the top level — meaning if
 * the two Xaero-targeting mixins under {@code mixin.xaero} were listed there like any other
 * mixin, a client <i>without</i> Xaero installed would hit a fatal "required mixin target class
 * not found" crash the moment Mixin tried to resolve {@code xaero.common.minimap.highlight
 * .HighlighterRegistry} / {@code xaero.map.WorldMapSession} — the same crash <i>class</i> as the
 * Phantasia incident earlier this project's history, just one layer lower (Mixin transform time
 * instead of Forge event-bus reflection time).
 * <p>
 * {@link #shouldApplyMixin} receives the target class as a plain {@code String}, evaluated
 * <b>before</b> Mixin attempts to actually resolve/load that class — this is the standard,
 * documented way to make one specific mixin in an otherwise-required config conditional on an
 * optional third-party mod's presence, confirmed against GTCEu's own {@code GTMixinPlugin} using
 * the exact same hook (for a different, config-driven purpose there, but the same mechanism).
 * Every other mixin in this config (currently just {@code DummyMixin}) is unaffected — this only
 * intercepts the two Xaero mixins by name.
 * <p>
 * Click-to-claim's "read a private field on GuiMap/Fullscreen" need used to be two more mixins
 * here ({@code @Accessor} interfaces) — removed after neither actually got applied at runtime for
 * reasons that didn't show up as a Mixin-side error (confirmed live via a {@code
 * ClassCastException} on the target class), in favor of plain reflection instead (see {@code
 * XaeroClaimClickBridge}/{@code JourneyMapClaimClickBridge}), which doesn't depend on this plugin
 * or Mixin at all.
 */
public class DomainsMixinConfigPlugin implements IMixinConfigPlugin {

    private static final String XAERO_MINIMAP_MIXIN = "net.phoenixvine.domains.mixin.xaero.DomainXaeroHighlighterRegistryMixin";
    private static final String XAERO_WORLDMAP_MIXIN = "net.phoenixvine.domains.mixin.xaero.DomainXaeroWorldMapSessionMixin";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // ModList.get() isn't populated yet at this point in Forge's lifecycle — this config's
        // mixins get PREPAREd during the early loading-screen module-reads step (see
        // DisplayWindow.updateModuleReads in the stack trace), well before FML's mod-construction
        // phase that builds ModList. Calling ModList.get().isLoaded(...) here threw an NPE
        // (ModList.get() itself was null) and crashed the client outright, with or without Xaero
        // installed. FMLLoader.getLoadingModList() is populated earlier (right after mod file
        // discovery/LOCATE) and is the documented-safe way to check mod presence from a mixin
        // config plugin — same fix already used by Oculus's own Vista/Indium/Indigo/Pixelmon
        // compat mixin plugins for this exact problem.
        if (mixinClassName.equals(XAERO_MINIMAP_MIXIN)) {
            return FMLLoader.getLoadingModList().getModFileById("xaerominimap") != null;
        }
        if (mixinClassName.equals(XAERO_WORLDMAP_MIXIN)) {
            return FMLLoader.getLoadingModList().getModFileById("xaeroworldmap") != null;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
