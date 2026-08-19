package net.phoenixvine.domains.mixin.xaero;

import net.phoenixvine.domains.integration.xaero.DomainClaimChunkHighlighter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.highlight.AbstractHighlighter;
import xaero.common.minimap.highlight.HighlighterRegistry;

/**
 * Registers {@link DomainClaimChunkHighlighter} with Xaero Minimap's {@code HighlighterRegistry}
 * right after it's constructed. {@code register(AbstractHighlighter)} is itself an ordinary
 * public method — this mixin exists only because Xaero doesn't yet expose a public hook to
 * obtain the registry instance at the right lifecycle moment (same gap GTCEu's own {@code
 * HighlighterRegistryMixin} works around, confirmed against their production code).
 * <p>
 * Only ever applied when Xaero's Minimap is actually installed — gated by {@code
 * DomainsMixinConfigPlugin#shouldApplyMixin}, since {@code phoenix_domains.mixins.json}'s
 * top-level {@code "required": true} would otherwise turn "Xaero isn't installed" (so this
 * mixin's target class doesn't exist) into a fatal crash, the same crash class the Phantasia
 * incident was, one layer lower (Mixin transform time instead of Forge event-bus reflection
 * time).
 */
@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class DomainXaeroHighlighterRegistryMixin {

    @Shadow
    public abstract void register(AbstractHighlighter highlighter);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void domains$registerClaimHighlighter(CallbackInfo ci) {
        this.register(new DomainClaimChunkHighlighter());
    }
}
