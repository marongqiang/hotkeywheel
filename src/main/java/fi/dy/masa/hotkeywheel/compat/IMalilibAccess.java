package fi.dy.masa.hotkeywheel.compat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import fi.dy.masa.hotkeywheel.hotkeys.WheelAction;
import fi.dy.masa.hotkeywheel.scan.KeyBindingComboScanner;

/**
 * Optional MaLiLib hotkey access; implement without importing malilib types.
 */
public interface IMalilibAccess
{
    void appendForCombo(String comboId, String comboIdUpper, Set<String> disabledFullLines, List<WheelAction> out);

    Map<String, List<KeyBindingComboScanner.MalilibLine>> buildScanLines();
}
