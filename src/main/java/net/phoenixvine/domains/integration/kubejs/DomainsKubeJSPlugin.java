package net.phoenixvine.domains.integration.kubejs;

import net.phoenixvine.domains.api.DomainAPI;
import net.phoenixvine.domains.api.DomainFeatureState;
import net.phoenixvine.domains.data.ChunkKey;
import net.phoenixvine.domains.data.Claim;
import net.phoenixvine.domains.data.ClaimFlag;
import net.phoenixvine.domains.data.ClaimPower;
import net.phoenixvine.domains.ownership.ClaimPermissions;
import net.phoenixvine.domains.ownership.DomainOwnership;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;

public class DomainsKubeJSPlugin extends KubeJSPlugin {

    @Override
    public void registerClasses(ScriptType type, ClassFilter filter) {
        filter.allow(DomainAPI.class);
        filter.allow(DomainFeatureState.class);
        filter.allow(ChunkKey.class);
        filter.allow(Claim.class);
        filter.allow(ClaimFlag.class);
        filter.allow(ClaimPower.class);
        filter.allow(ClaimPermissions.class);
        filter.allow(DomainOwnership.class);
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("DomainAPI", DomainAPI.class);
        event.add("ClaimFlag", ClaimFlag.class);
        event.add("DomainFeatureState", DomainFeatureState.class);
    }
}
