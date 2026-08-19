package net.phoenixvine.domains.integration.claimclick;

import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.network.C2SDomainActionPacket;
import net.phoenixvine.domains.network.DomainNetwork;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;

/**
 * The actual claim/unclaim/chunkload network calls for click-to-claim on Xaero's World
 * Map/JourneyMap — shared by {@code XaeroClaimClickBridge}/{@code JourneyMapClaimClickBridge} so
 * neither duplicates this logic. Mirrors {@code SolarisClaimMapScreen#performClaimAction}/{@code
 * #performUnclaimAction} exactly, same button scheme and all: {@code claim} selects left-click
 * -family (claim/chunkload-on) versus right-click-family (unclaim/chunkload-off) behavior, and
 * {@code chunkloadToggle} is the exact same Shift modifier {@code SolarisClaimMapScreen} itself
 * uses (see {@code ClaimClickHandler}). Same "only claim if unclaimed / only unclaim if claimed"
 * guard, just without that screen's
 * drag-to-mass-claim state — this integration only ever reacts to a single mouse press, not a
 * drag, so there's no per-frame repeat to guard against. Contains zero references to any optional
 * mod's types — safe to call unconditionally once a caller has already resolved real world
 * coordinates.
 */
public final class ClaimClickActions {

    private ClaimClickActions() {}

    public static void perform(int chunkX, int chunkZ, boolean claim, boolean chunkloadToggle) {
        S2CDomainSyncPacket.ClaimEntry entry = ClientDomainCache.entryAt(chunkX, chunkZ);
        if (chunkloadToggle) {
            if (claim) {
                if (entry == null) {
                    DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.claim(chunkX, chunkZ));
                }
                DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.setChunkloaded(chunkX, chunkZ, true));
            } else if (entry != null) {
                DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.setChunkloaded(chunkX, chunkZ, false));
            }
            return;
        }

        if (claim) {
            if (entry == null) {
                DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.claim(chunkX, chunkZ));
            }
        } else if (entry != null) {
            DomainNetwork.CHANNEL.sendToServer(C2SDomainActionPacket.unclaim(chunkX, chunkZ));
        }
    }
}
