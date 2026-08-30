package net.phoenixvine.domains.integration.solaris;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;
import net.phoenixvine.solaris.client.overlay.SolarisOverlay;

import java.util.Optional;

public class DomainClaimOverlay implements SolarisOverlay {

    private static final int TINT_ALPHA = 0x99;
    private static final int CHUNKLOADED_RGB = 0xFFD700;

    @Override
    public Optional<Integer> colorAt(ResourceLocation dimension, int chunkX, int chunkZ) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(dimension)) return Optional.empty();

        S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(chunkX, chunkZ);
        if (entry == null) return Optional.empty();

        int rgb = entry.chunkloaded() ? CHUNKLOADED_RGB : (entry.color() & 0xFFFFFF);
        return Optional.of((TINT_ALPHA << 24) | rgb);
    }
}
