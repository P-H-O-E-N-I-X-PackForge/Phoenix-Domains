package net.phoenixvine.domains.integration.claimclick;

import net.phoenixvine.domains.client.ClientDomainCache;
import net.phoenixvine.domains.network.C2SDomainActionPacket;
import net.phoenixvine.domains.network.DomainNetwork;
import net.phoenixvine.domains.network.S2CDomainSyncPacket;

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
