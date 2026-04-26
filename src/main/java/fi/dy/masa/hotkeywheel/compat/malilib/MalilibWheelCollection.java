package fi.dy.masa.hotkeywheel.compat.malilib;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeybindCategory;
import fi.dy.masa.hotkeywheel.hotkeys.HotkeyWheelKeyComboUtil;
import fi.dy.masa.hotkeywheel.hotkeys.WheelAction;

/**
 * Collects MaLiLib hotkeys bound to the same physical combo as the current key event.
 */
public final class MalilibWheelCollection
{
    private MalilibWheelCollection() {}

    public static void appendForCombo(String comboId, String comboIdUpper, Set<String> disabledFullLines, List<WheelAction> out)
    {
        Set<IHotkey> seen = new HashSet<>();
        List<KeybindCategory> cats = InputEventHandler.getKeybindManager().getKeybindCategories();
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
                boolean matchesEventCombo = false;
                for (String cid : HotkeyWheelKeyComboUtil.getComboIdsForStorageString(storage))
                {
                    if (cid != null && cid.equalsIgnoreCase(comboId))
                    {
                        matchesEventCombo = true;
                        break;
                    }
                }
                if (matchesEventCombo == false) continue;
                String excl = MalilibIds.exclusionId(modName, h.getName());
                String dis = comboIdUpper + "|" + excl;
                if (disabledFullLines != null && disabledFullLines.contains(dis)) continue;
                if (seen.add(h)) out.add(new MalilibWheelAction(h, modName));
            }
        }
    }
}
