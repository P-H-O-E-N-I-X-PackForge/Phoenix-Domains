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

/**
 * World Map (fullscreen map) counterpart to {@code DomainXaeroHighlighterRegistryMixin} — a
 * separate mod/jar from Xaero's Minimap with its own {@code HighlighterRegistry}/{@code
 * ChunkHighlighter} types (see {@link DomainClaimWorldMapChunkHighlighter}'s own doc). Unlike the
 * minimap side, the world map's registry only ever exists as a local variable inside {@code
 * WorldMapSession.init(...)} (confirmed via {@code javap} against the real jar — no field/getter
 * exposes it), so registration needs MixinExtras' {@code @Local} to actually reach it — the exact
 * same technique GTCEu's own production {@code WorldMapSessionMixin} uses for this identical gap.
 * Injects right after {@code HighlighterRegistry.end()} is called, the same insertion point
 * GTCEu's mixin uses.
 * <p>
 * Only ever applied when Xaero's World Map is actually installed — see {@code
 * DomainsMixinConfigPlugin}, same reasoning as the minimap mixin's own doc.
 */
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
