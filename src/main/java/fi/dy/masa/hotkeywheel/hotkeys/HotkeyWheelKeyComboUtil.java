package fi.dy.masa.hotkeywheel.hotkeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import fi.dy.masa.hotkeywheel.util.HotkeyKeyCodes;

/**
 * Whitelist "combo" strings (e.g. CTRL,SHIFT,V) and matching against KeyBinding and keyboard events.
 */
public final class HotkeyWheelKeyComboUtil
{
    private HotkeyWheelKeyComboUtil() {}

    public static String keyToMainToken(InputUtil.Key key)
    {
        if (key == null || key.equals(InputUtil.UNKNOWN_KEY)) return "";
        String tk = key.getTranslationKey();
        if (tk != null && tk.startsWith("key.mouse."))
        {
            String s = tk.substring("key.mouse.".length());
            return switch (s)
            {
                case "left" -> "MOUSE.1";
                case "right" -> "MOUSE.2";
                case "middle" -> "MOUSE.3";
                case "4" -> "MOUSE.4";
                case "5" -> "MOUSE.5";
                case "6" -> "MOUSE.6";
                case "7" -> "MOUSE.7";
                case "8" -> "MOUSE.8";
                default -> s.matches("\\d+") ? ("MOUSE." + s) : "MOUSE.1";
            };
        }
        String n = HotkeyKeyCodes.getNameForKey(key.getCode());
        return n != null ? n : "KEY" + key.getCode();
    }

    public static List<String> getComboIdsForKeyBinding(KeyBinding keyBinding)
    {
        if (keyBinding == null || keyBinding.isUnbound()) return List.of();
        return Collections.singletonList(buildComboIdFromKey(
                InputUtil.fromTranslationKey(keyBinding.getBoundKeyTranslationKey())));
    }

    public static String buildComboIdFromKey(InputUtil.Key key)
    {
        if (key == null || key.equals(InputUtil.UNKNOWN_KEY)) return "";
        return keyToMainToken(key).toUpperCase(Locale.ROOT);
    }

    public static String buildComboIdFromEvent(int keyCode, int modifiers)
    {
        if (keyCode == HotkeyKeyCodes.KEY_NONE) return "";
        String main = HotkeyKeyCodes.getNameForKey(keyCode);
        if (main == null || main.isEmpty()) return "";
        List<String> parts = new ArrayList<>(5);
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) parts.add("CTRL");
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) parts.add("SHIFT");
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) parts.add("ALT");
        if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0) parts.add("SUPER");
        parts.add(main.toUpperCase(Locale.ROOT));
        return String.join(",", parts);
    }

    /**
     * Build a combo id from the current key event and the set of pressed non-modifier keys.
     * This allows two-key combos like "M+G" to map to a distinct wheel group ("M,G").
     */
    public static String buildComboIdFromEventWithHeldKeys(int keyCode, int modifiers, Set<Integer> heldKeys)
    {
        if (keyCode == HotkeyKeyCodes.KEY_NONE) return "";
        List<String> parts = new ArrayList<>(8);
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) parts.add("CTRL");
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) parts.add("SHIFT");
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) parts.add("ALT");
        if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0) parts.add("SUPER");

        List<String> mains = new ArrayList<>(4);
        if (heldKeys != null)
        {
            for (int k : heldKeys)
            {
                if (k == HotkeyKeyCodes.KEY_NONE) continue;
                // Ignore pure modifier keys here; they are represented above.
                if (k == GLFW.GLFW_KEY_LEFT_CONTROL || k == GLFW.GLFW_KEY_RIGHT_CONTROL) continue;
                if (k == GLFW.GLFW_KEY_LEFT_SHIFT || k == GLFW.GLFW_KEY_RIGHT_SHIFT) continue;
                if (k == GLFW.GLFW_KEY_LEFT_ALT || k == GLFW.GLFW_KEY_RIGHT_ALT) continue;
                if (k == GLFW.GLFW_KEY_LEFT_SUPER || k == GLFW.GLFW_KEY_RIGHT_SUPER) continue;
                String n = HotkeyKeyCodes.getNameForKey(k);
                if (n != null && n.isEmpty() == false) mains.add(n.toUpperCase(Locale.ROOT));
            }
        }
        // Ensure the current key is part of the combo even if heldKeys tracking is off.
        String cur = HotkeyKeyCodes.getNameForKey(keyCode);
        if (cur != null && cur.isEmpty() == false)
        {
            String u = cur.toUpperCase(Locale.ROOT);
            if (mains.contains(u) == false) mains.add(u);
        }
        // Stable ordering so storage/scans match runtime.
        Collections.sort(mains);
        parts.addAll(mains);
        return String.join(",", parts);
    }

    public static List<String> getComboIdsForStorageString(String storage)
    {
        if (storage == null) return List.of();
        String[] parts = storage.split(",");
        if (parts.length == 0) return List.of();
        boolean ctrl = false, shift = false, alt = false, superKey = false;
        List<String> mains = new ArrayList<>(4);
        for (String raw : parts)
        {
            if (raw == null) continue;
            String k = raw.trim().toUpperCase(Locale.ROOT);
            if (k.isEmpty()) continue;
            if (k.equals("LEFT_CONTROL") || k.equals("RIGHT_CONTROL") || k.equals("CTRL")) ctrl = true;
            else if (k.equals("LEFT_SHIFT") || k.equals("RIGHT_SHIFT") || k.equals("SHIFT")) shift = true;
            else if (k.equals("LEFT_ALT") || k.equals("RIGHT_ALT") || k.equals("ALT")) alt = true;
            else if (k.equals("LEFT_SUPER") || k.equals("RIGHT_SUPER") || k.equals("SUPER")) superKey = true;
            else mains.add(k);
        }
        if (mains.isEmpty()) return List.of();
        Collections.sort(mains);
        List<String> combo = new ArrayList<>(8);
        if (ctrl) combo.add("CTRL");
        if (shift) combo.add("SHIFT");
        if (alt) combo.add("ALT");
        if (superKey) combo.add("SUPER");
        combo.addAll(mains);
        return List.of(String.join(",", combo));
    }

    public static String comboIdToDisplayString(String comboId)
    {
        if (comboId == null) return "";
        String[] parts = comboId.split(",");
        if (parts.length == 0) return "";
        StringBuilder sb = new StringBuilder(32);
        for (String p0 : parts)
        {
            String p = p0.trim();
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" + ");
            if (p.equalsIgnoreCase("CTRL")) sb.append("Ctrl");
            else if (p.equalsIgnoreCase("SHIFT")) sb.append("Shift");
            else if (p.equalsIgnoreCase("ALT")) sb.append("Alt");
            else if (p.equalsIgnoreCase("SUPER")) sb.append("Win");
            else sb.append(p);
        }
        return sb.toString();
    }
}
