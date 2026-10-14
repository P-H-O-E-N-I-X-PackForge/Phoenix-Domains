package net.phoenixvine.domains.client;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.phoenixvine.domains.PhoenixDomains;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = PhoenixDomains.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DomainKeybinds {

    public static final KeyMapping OPEN_MAP = new KeyMapping(
            "key.phoenix_domains.map",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "key.categories.phoenix_domains");

    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.phoenix_domains.toggle_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.phoenix_domains");

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAP);
        event.register(TOGGLE_HUD);
    }
}
