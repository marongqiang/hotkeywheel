package fi.dy.masa.hotkeywheel.compat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.resource.language.I18n;
import fi.dy.masa.hotkeywheel.compat.malilib.MalilibIds;
import fi.dy.masa.hotkeywheel.hotkeys.HotkeyWheelKeyComboUtil;
import fi.dy.masa.hotkeywheel.hotkeys.WheelAction;
import fi.dy.masa.hotkeywheel.scan.KeyBindingComboScanner;

/**
 * Default {@link IMalilibAccess}: MaLiLib via reflection (no static imports of malilib types at compile time in callers).
 */
public final class MalilibAccessReflective implements IMalilibAccess
{
    public static final MalilibAccessReflective INSTANCE = new MalilibAccessReflective();

    private static final String C_INPUT = "fi.dy.masa.malilib.event.InputEventHandler";
    private Object cachedKeybindManager;
    private Method mGetKeybindCategories;
    private Method mGetModName;
    private Method mGetHotkeys;
    private Method mGetName;
    private Method mGetKeybind;
    private Method mIsValid;
    private Method mGetStringValue;
    private volatile boolean probed;

    private MalilibAccessReflective() { }

    private boolean ensureProbed()
    {
        if (this.probed) return this.cachedKeybindManager != null;
        this.probed = true;
        try
        {
            Class<?> cInput = Class.forName(C_INPUT);
            Class<?> cMgr = Class.forName("fi.dy.masa.malilib.hotkeys.IKeybindManager");
            Method mGetMgr = cInput.getMethod("getKeybindManager");
            this.cachedKeybindManager = mGetMgr.invoke(null);
            this.mGetKeybindCategories = cMgr.getMethod("getKeybindCategories");
            Class<?> cCat = Class.forName("fi.dy.masa.malilib.hotkeys.KeybindCategory");
            this.mGetModName = cCat.getMethod("getModName");
            this.mGetHotkeys = cCat.getMethod("getHotkeys");
            Class<?> cIHotkey = Class.forName("fi.dy.masa.malilib.hotkeys.IHotkey");
            this.mGetName = cIHotkey.getMethod("getName");
            Class<?> cIKeybind = Class.forName("fi.dy.masa.malilib.hotkeys.IKeybind");
            this.mGetKeybind = cIHotkey.getMethod("getKeybind");
            this.mIsValid = cIKeybind.getMethod("isValid");
            this.mGetStringValue = cIKeybind.getMethod("getStringValue");
        }
        catch (Throwable t)
        {
            this.cachedKeybindManager = null;
            this.mGetKeybindCategories = null;
        }
        return this.cachedKeybindManager != null;
    }

    @Override
    public void appendForCombo(
            String comboId,
            String comboIdUpper,
            Set<String> disabledFullLines,
            List<WheelAction> out)
    {
        if (this.ensureProbed() == false || ReflectedMalilibWheelAction.reflectionReady() == false) return;
        try
        {
            @SuppressWarnings("unchecked")
            List<Object> cats = (List<Object>) this.mGetKeybindCategories.invoke(this.cachedKeybindManager);
            Set<Object> seen = new HashSet<>();
            for (Object cat : cats)
            {
                if (cat == null) continue;
                String modName = (String) this.mGetModName.invoke(cat);
                if (modName == null) modName = "";
                @SuppressWarnings("unchecked")
                List<Object> hotkeys = (List<Object>) this.mGetHotkeys.invoke(cat);
                for (Object h : hotkeys)
                {
                    if (h == null) continue;
                    Object kb = this.mGetKeybind.invoke(h);
                    if (kb == null) continue;
                    if (((Boolean) this.mIsValid.invoke(kb)) == false) continue;
                    String storage = (String) this.mGetStringValue.invoke(kb);
                    if (storage == null || storage.trim().isEmpty()) continue;
                    boolean match = false;
                    for (String cid : HotkeyWheelKeyComboUtil.getComboIdsForStorageString(storage))
                    {
                        if (cid != null && cid.equalsIgnoreCase(comboId))
                        {
                            match = true;
                            break;
                        }
                    }
                    if (match == false) continue;
                    String hName = (String) this.mGetName.invoke(h);
                    if (hName == null) hName = "";
                    String excl = MalilibIds.exclusionId(modName, hName);
                    String dis = comboIdUpper + "|" + excl;
                    if (disabledFullLines != null && disabledFullLines.contains(dis)) continue;
                    if (seen.add(h)) out.add(new ReflectedMalilibWheelAction(h, modName));
                }
            }
        }
        catch (Throwable ignored) { }
    }

    @Override
    public Map<String, List<KeyBindingComboScanner.MalilibLine>> buildScanLines()
    {
        Map<String, List<KeyBindingComboScanner.MalilibLine>> byCombo = new HashMap<>();
        if (this.ensureProbed() == false) return byCombo;
        try
        {
            @SuppressWarnings("unchecked")
            List<Object> cats = (List<Object>) this.mGetKeybindCategories.invoke(this.cachedKeybindManager);
            Map<String, Set<String>> addedExclPerCombo = new HashMap<>();
            for (Object cat : cats)
            {
                if (cat == null) continue;
                String modName = (String) this.mGetModName.invoke(cat);
                if (modName == null) modName = "";
                @SuppressWarnings("unchecked")
                List<Object> hotkeys = (List<Object>) this.mGetHotkeys.invoke(cat);
                for (Object h : hotkeys)
                {
                    if (h == null) continue;
                    Object kb = this.mGetKeybind.invoke(h);
                    if (kb == null) continue;
                    if (((Boolean) this.mIsValid.invoke(kb)) == false) continue;
                    String storage = (String) this.mGetStringValue.invoke(kb);
                    if (storage == null || storage.trim().isEmpty()) continue;
                    String hName = (String) this.mGetName.invoke(h);
                    if (hName == null) hName = "";
                    String label = I18n.translate(hName);
                    String excl = MalilibIds.exclusionId(modName, hName);
                    for (String raw : HotkeyWheelKeyComboUtil.getComboIdsForStorageString(storage))
                    {
                        if (raw == null || raw.isEmpty()) continue;
                        String u = raw.toUpperCase(Locale.ROOT);
                        Set<String> per = addedExclPerCombo.computeIfAbsent(u, t -> new HashSet<>());
                        if (per.add(excl) == false) continue;
                        byCombo.computeIfAbsent(u, t -> new java.util.ArrayList<>())
                                .add(new KeyBindingComboScanner.MalilibLine(modName, label, excl));
                    }
                }
            }
        }
        catch (Throwable ignored) { }
        return byCombo;
    }
}
