package net.phoenixvine.domains.mixin.xaero;

import net.phoenixvine.domains.integration.xaero.DomainClaimChunkHighlighter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.highlight.AbstractHighlighter;
import xaero.common.minimap.highlight.HighlighterRegistry;

@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class DomainXaeroHighlighterRegistryMixin {

    @Shadow
    public abstract void register(AbstractHighlighter highlighter);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void domains$registerClaimHighlighter(CallbackInfo ci) {
        this.register(new DomainClaimChunkHighlighter());
    }
}
