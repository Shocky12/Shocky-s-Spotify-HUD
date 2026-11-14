package dev.shocky.spotifyhud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShockysSpotifyHud implements ClientModInitializer {
    public static final String MOD_ID = "shockysspotifyhud";
    public static final Logger LOGGER = LoggerFactory.getLogger("Shocky's Spotify HUD");
    public static final SpotifyBridge BRIDGE = new SpotifyBridge();

    private static KeyBinding openKey;
    private static KeyBinding playPauseKey;
    private static KeyBinding nextKey;
    private static KeyBinding prevKey;

    @Override
    public void onInitializeClient() {
        HudConfig.load();
        BRIDGE.start();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shockysspotifyhud.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
        playPauseKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shockysspotifyhud.playpause", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        nextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shockysspotifyhud.next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        prevKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shockysspotifyhud.prev", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.of(MOD_ID, "spotify_hud"),
                HudRenderer::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                client.setScreen(new ControlsScreen(null));
            }
            while (playPauseKey.wasPressed()) BRIDGE.send("playpause");
            while (nextKey.wasPressed()) BRIDGE.send("next");
            while (prevKey.wasPressed()) BRIDGE.send("prev");
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            HudConfig.save();
            BRIDGE.stop();
        });
    }
}
