package de.hackmod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Einstiegspunkt: Taste C oeffnet das ClickGUI. */
public class HackModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Modules.init();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("hackmod", "main"));
        KeyMapping openGui = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.hackmod.gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category));

        KeyMapping pos1 = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.hackmod.pos1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, category));
        KeyMapping pos2 = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.hackmod.pos2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, category));
        KeyMapping clearArea = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.hackmod.cleararea", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, category));

        // X-Ray-Ressourcenpaket (wird vom X-Ray-Modul automatisch an- und ausgeschaltet)
        ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath("hackmod", "xray"),
                FabricLoader.getInstance().getModContainer("hackmod").orElseThrow(),
                Component.literal("HackMod X-Ray"), PackActivationType.NORMAL);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (pos1.consumeClick()) Area.set(mc, 1);
            while (pos2.consumeClick()) Area.set(mc, 2);
            while (clearArea.consumeClick()) Area.clear(mc);
            while (openGui.consumeClick()) {
                if (mc.screen == null && mc.player != null) mc.setScreen(new ClickGui());
            }
            Modules.tick(mc);
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("hackmod", "hud"),
                (graphics, deltaTracker) -> Modules.hud(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
    }
}
