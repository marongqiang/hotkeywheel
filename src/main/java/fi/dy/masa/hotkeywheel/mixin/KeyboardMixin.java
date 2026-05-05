package fi.dy.masa.hotkeywheel.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Keyboard;
import fi.dy.masa.hotkeywheel.HotkeyWheelClient;
import fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore;
import fi.dy.masa.hotkeywheel.hotkeys.HotkeyWheelManager;
import fi.dy.masa.hotkeywheel.util.HotkeyKeyCodes;

@Mixin(Keyboard.class)
public class KeyboardMixin
{
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void hotkeywheel$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci)
    {
        if (key == HotkeyKeyCodes.KEY_NONE) return;
        if (HotkeyWheelManager.INSTANCE == null) return;
        boolean handled = HotkeyWheelManager.INSTANCE.onKeyboardKey(key, scancode, action, modifiers);
        if (handled && HotkeyWheelManager.INSTANCE.isOpen())
        {
            if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging() && action == GLFW.GLFW_RELEASE)
            {
                HotkeyWheelClient.LOGGER.info(
                        "HotkeyWheel keyboard: cancel onKey RELEASE key={} scancode={} modifiers={}",
                        key,
                        scancode,
                        modifiers);
            }
            ci.cancel();
        }
    }
}
