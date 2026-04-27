package fi.dy.masa.hotkeywheel.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import fi.dy.masa.hotkeywheel.HotkeyWheelClient;
import fi.dy.masa.hotkeywheel.hotkeys.HotkeyWheelKeyComboUtil;
import fi.dy.masa.hotkeywheel.util.KeyBindingModNames;

/**
 * Group vanilla {@link KeyBinding} and (optionally) MaLiLib hotkeys by the same physical bind.
 */
public final class KeyBindingComboScanner
{
    public record ScannedKeyBinding(
            String translationKey,
            String functionNameI18n,
            String modId,
            String modName)
    { }

    /** One Masa family hotkey line for a combo (exclusion id is {@code mli:...}, not a vanilla translation key). */
    public record MalilibLine(
            String modName,
            String functionNameI18n,
            String exclusionId)
    { }

    public record ScanRow(
            KeyBinding keyBinding,
            String modName,
            String functionNameI18n,
            /** Vanilla: {@link KeyBinding#getTranslationKey()}; MaLiLib: {@link MalilibLine#exclusionId()}. */
            String exclusionKey)
    {
        public static ScanRow vanilla(KeyBinding kb, ScannedKeyBinding meta)
        {
            return new ScanRow(
                    kb,
                    meta.modName(),
                    meta.functionNameI18n(),
                    kb.getTranslationKey());
        }

        public static ScanRow fromMalilib(MalilibLine m)
        {
            return new ScanRow(null, m.modName(), m.functionNameI18n(), m.exclusionId());
        }

        public boolean isMalilib()
        {
            return this.keyBinding == null;
        }
    }

    public record ComboGroup(
            String comboId,
            int entryCount,
            List<ScanRow> rows)
    { }

    private KeyBindingComboScanner() { }

    public static List<ComboGroup> scan(MinecraftClient client)
    {
        if (client == null || client.options == null) return List.of();
        KeyBinding our = HotkeyWheelClient.OPEN_CONFIG_KEY;
        Map<String, LinkedHashSet<KeyBinding>> byCombo = new HashMap<>();
        GameOptions o = client.options;
        for (KeyBinding kb : o.allKeys)
        {
            if (kb == null) continue;
            if (our != null && kb == our) continue;
            if (kb.isUnbound()) continue;
            for (String cid : HotkeyWheelKeyComboUtil.getComboIdsForKeyBinding(kb))
            {
                if (cid == null || cid.isEmpty()) continue;
                String u = cid.toUpperCase(Locale.ROOT);
                byCombo.computeIfAbsent(u, t -> new LinkedHashSet<>()).add(kb);
            }
        }
        Map<String, List<MalilibLine>> mlByCombo = Map.of();
        if (FabricLoader.getInstance().isModLoaded("malilib"))
        {
            try
            {
                mlByCombo = fi.dy.masa.hotkeywheel.compat.MalilibAccessReflective.INSTANCE.buildScanLines();
            }
            catch (Throwable t)
            {
                HotkeyWheelClient.LOGGER.warn("Hotkey Wheel: optional MaLiLib scan failed; using vanilla keybinds only in this list.", t);
            }
        }
        Set<String> all = new HashSet<>();
        all.addAll(byCombo.keySet());
        all.addAll(mlByCombo.keySet());
        List<ComboGroup> out = new ArrayList<>();
        for (String comboUpper : all)
        {
            List<ScanRow> rows = new ArrayList<>();
            List<KeyBinding> kbs;
            {
                LinkedHashSet<KeyBinding> s = byCombo.get(comboUpper);
                if (s == null || s.isEmpty()) kbs = List.of();
                else
                {
                    kbs = new ArrayList<>(s);
                    kbs.sort(Comparator.comparing(KeyBinding::getTranslationKey));
                }
            }
            for (KeyBinding kb : kbs)
            {
                String tk = kb.getTranslationKey();
                String modId = KeyBindingModNames.getModId(tk);
                rows.add(ScanRow.vanilla(
                        kb,
                        new ScannedKeyBinding(
                                tk,
                                net.minecraft.client.resource.language.I18n.translate(tk),
                                modId,
                                KeyBindingModNames.getModDisplayName(modId))));
            }
            for (MalilibLine line : mlByCombo.getOrDefault(comboUpper, List.of()))
            {
                rows.add(ScanRow.fromMalilib(line));
            }
            if (rows.isEmpty()) continue;
            // Stable order: vanilla first, then Masa (mod name, then exclusion id)
            rows.sort(Comparator
                    .comparingInt((ScanRow r) -> r.isMalilib() ? 1 : 0)
                    .thenComparing(r -> r.modName() != null ? r.modName() : "", String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(r -> r.exclusionKey() != null ? r.exclusionKey() : "", String.CASE_INSENSITIVE_ORDER));
            out.add(new ComboGroup(comboUpper, rows.size(), Collections.unmodifiableList(rows)));
        }
        out.sort(Comparator
                .comparing((ComboGroup g) -> g.entryCount() < 2)
                .thenComparing(Comparator.comparingInt(ComboGroup::entryCount).reversed())
                .thenComparing(a -> HotkeyWheelKeyComboUtil.comboIdToDisplayString(a.comboId().toUpperCase(Locale.ROOT))));
        return out;
    }
}
