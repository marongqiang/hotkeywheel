package fi.dy.masa.hotkeywheel.compat.malilib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.resource.language.I18n;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeybindCategory;
import fi.dy.masa.hotkeywheel.hotkeys.HotkeyWheelKeyComboUtil;
import fi.dy.masa.hotkeywheel.scan.KeyBindingComboScanner;

/**
 * Iterates {@link KeybindCategory} hotkeys. Only used when the malilib mod is present; caller must
 * wrap in try/catch to tolerate API/ABI issues.
 */
public final class MalilibKeybindScanHelper
{
    private MalilibKeybindScanHelper() {}

    /**
     * @return combo id (upper) → list of Masa hotkey lines for the config detail screen
     */
    public static Map<String, List<KeyBindingComboScanner.MalilibLine>> buildLinesByCombo()
    {
        List<KeybindCategory> cats = InputEventHandler.getKeybindManager().getKeybindCategories();
        Map<String, List<KeyBindingComboScanner.MalilibLine>> byCombo = new HashMap<>();
        Map<String, Set<String>> addedExclPerCombo = new HashMap<>();
        for (KeybindCategory cat : cats)
        {
            if (cat == null) continue;
            String modName = cat.getModName();
            if (modName == null) modName = "";
            for (IHotkey h : cat.getHotkeys())
            {
                if (h == null) continue;
                IKeybind kb = h.getKeybind();
                if (kb == null) continue;
                if (kb.isValid() == false) continue;
                String storage = kb.getStringValue();
                if (storage == null || storage.trim().isEmpty()) continue;
                String label = I18n.translate(h.getName());
                String excl = MalilibIds.exclusionId(modName, h.getName());
                for (String raw : HotkeyWheelKeyComboUtil.getComboIdsForStorageString(storage))
                {
                    if (raw == null || raw.isEmpty()) continue;
                    String u = raw.toUpperCase(Locale.ROOT);
                    Set<String> per = addedExclPerCombo.computeIfAbsent(u, t -> new HashSet<>());
                    if (per.add(excl) == false) continue;
                    byCombo.computeIfAbsent(u, t -> new ArrayList<>())
                            .add(new KeyBindingComboScanner.MalilibLine(modName, label, excl));
                }
            }
        }
        return byCombo;
    }
}
