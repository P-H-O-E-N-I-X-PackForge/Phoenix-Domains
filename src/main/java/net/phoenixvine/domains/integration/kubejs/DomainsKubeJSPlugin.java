package net.phoenixvine.domains.integration.kubejs;

import net.phoenixvine.domains.api.DomainAPI;
import net.phoenixvine.domains.api.DomainFeatureState;
import net.phoenixvine.domains.data.ChunkKey;
import net.phoenixvine.domains.data.Claim;
import net.phoenixvine.domains.data.ClaimFlag;
import net.phoenixvine.domains.data.ClaimPower;
import net.phoenixvine.domains.ownership.ClaimPermissions;
import net.phoenixvine.domains.ownership.DomainOwnership;

import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

public class DomainsKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerClasses(ClassFilter filter) {
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
    public void registerBindings(BindingRegistry registry) {
        registry.add("DomainAPI", DomainAPI.class);
        registry.add("ClaimFlag", ClaimFlag.class);
        registry.add("DomainFeatureState", DomainFeatureState.class);
    }
}
