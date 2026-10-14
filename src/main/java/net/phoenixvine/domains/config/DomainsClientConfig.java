package net.phoenixvine.domains.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class DomainsClientConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SHOW_HUD;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("hud");
        SHOW_HUD = builder
                .comment("Show the top-of-screen line naming whoever owns the chunk you're standing in. " +
                        "Can also be toggled in-game with the keybind (default unbound).")
                .define("showHud", true);
        builder.pop();

        SPEC = builder.build();
    }

    private DomainsClientConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }
}
