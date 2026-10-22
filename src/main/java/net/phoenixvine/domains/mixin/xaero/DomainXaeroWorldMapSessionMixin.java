package net.phoenixvine.domains.mixin.xaero;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.phoenixvine.domains.integration.xaero.DomainClaimWorldMapChunkHighlighter;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.WorldMapSession;
import xaero.map.highlight.HighlighterRegistry;

@Mixin(value = WorldMapSession.class, remap = false)
public abstract class DomainXaeroWorldMapSessionMixin {

    @Inject(
            method = "init",
            at = @At(value = "INVOKE", target = "Lxaero/map/highlight/HighlighterRegistry;end()V"))
    private void domains$registerClaimHighlighter(ClientPacketListener connection, long biomeZoomSeed,
                                                  CallbackInfo ci, @Local HighlighterRegistry highlighterRegistry) {
        highlighterRegistry.register(new DomainClaimWorldMapChunkHighlighter());
    }
}
