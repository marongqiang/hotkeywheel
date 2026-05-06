package fi.dy.masa.hotkeywheel.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Mouse;
import fi.dy.masa.hotkeywheel.hotkeys.HotkeyWheelManager;

/**
 * While the wheel is open, vanilla mouse-button handling is skipped; left-click is read via GLFW in
 * {@link HotkeyWheelManager#tick()} so other mods cannot consume {@link Mouse#onMouseButton} first.
 */
@Mixin(Mouse.class)
public class MouseMixin
{
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void hotkeywheel$swallowMouseWhileWheelOpen(long window, int button, int action, int mods, CallbackInfo ci)
    {
        if (HotkeyWheelManager.INSTANCE != null && HotkeyWheelManager.INSTANCE.isOpen())
        {
            ci.cancel();
        }
    }
}
