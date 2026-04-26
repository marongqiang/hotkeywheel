package fi.dy.masa.hotkeywheel;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore;
import fi.dy.masa.hotkeywheel.gui.HotkeyWheelConfigScreen;
import fi.dy.masa.hotkeywheel.hotkeys.HotkeyWheelManager;

public class HotkeyWheelClient implements ClientModInitializer
{
    public static final Logger LOGGER = LoggerFactory.getLogger("hotkeywheel");
    public static KeyBinding OPEN_CONFIG_KEY;

    @Override
    public void onInitializeClient()
    {
        HotkeyWheelConfigStore.INSTANCE.load();
        OPEN_CONFIG_KEY = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.hotkeywheel.openconfig", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.category.hotkeywheel"));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudRenderCallback.EVENT.register((ctx, t) -> HotkeyWheelManager.INSTANCE.renderOverlay(ctx));
    }

    private void onTick(MinecraftClient client)
    {
        HotkeyWheelManager.INSTANCE.tick();
        while (OPEN_CONFIG_KEY.wasPressed())
        {
            client.setScreen(new HotkeyWheelConfigScreen(client.currentScreen));
        }
    }
}
