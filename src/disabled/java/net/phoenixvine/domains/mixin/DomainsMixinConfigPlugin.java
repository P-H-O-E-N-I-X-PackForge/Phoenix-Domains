package net.phoenixvine.domains.mixin;

import net.minecraftforge.fml.loading.FMLLoader;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

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
