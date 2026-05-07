package fi.dy.masa.hotkeywheel.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import fi.dy.masa.hotkeywheel.hotkeys.WheelAction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import fi.dy.masa.hotkeywheel.HotkeyWheelClient;
import fi.dy.masa.hotkeywheel.HotkeyWheelReference;
import fi.dy.masa.hotkeywheel.util.HotkeyWheelDefaultIcons;

/**
 * hotkeywheel.json: enabled combo ids, per-combo per-action exclusions.
 */
public final class HotkeyWheelConfigStore
{
    public static final HotkeyWheelConfigStore INSTANCE = new HotkeyWheelConfigStore();

    private static final String FILE_NAME = HotkeyWheelReference.MOD_ID + ".json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Uppercase combo ids (e.g. V, CTRL,SHIFT,B) for which the wheel may open. */
    private final Set<String> enabledCombos = new LinkedHashSet<>();
    private final List<String> disabledOnCombo = new ArrayList<>();
    private final Map<String, String> labelOverrides = new HashMap<>();
    private final Map<String, String> iconItemIds = new HashMap<>();
    private final Set<String> hiddenFromWheel = new LinkedHashSet<>();
    private final List<String> wheelActionSortOrder = new ArrayList<>();
    private int shortLabelMaxChars = 20;
    // UI / renderer options (reserved for customization)
    private boolean wheelShowDividers = true;
    private float wheelInnerRingAlpha = 0.72f;
    private float wheelOuterRingAlpha = 0.78f;
    private int wheelHoverTintColor = 0xFFFFFFFF;
    private float wheelIconScaleInner = 1.10f;
    private float wheelIconScaleOuter = 1.18f;
    private float wheelRingGapPx = 10f;
    private String wheelTheme = "tactical"; // "tactical" | "clean_light"
    private boolean wheelDebugLogging = false;
    private final Path file;

    private HotkeyWheelConfigStore()
    {
        this.file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public Set<String> getEnabledCombos() { return Collections.unmodifiableSet(this.enabledCombos); }

    public boolean isComboWheelEnabled(String comboIdUpper)
    {
        if (comboIdUpper == null) return false;
        return this.enabledCombos.contains(comboIdUpper.trim().toUpperCase(Locale.ROOT));
    }

    public void setComboWheelEnabled(String comboIdUpper, boolean on)
    {
        if (comboIdUpper == null) return;
        String u = comboIdUpper.trim().toUpperCase(Locale.ROOT);
        if (u.isEmpty()) return;
        if (on) this.enabledCombos.add(u);
        else this.enabledCombos.remove(u);
    }

    public List<String> getDisabledOnCombo() { return this.disabledOnCombo; }

    public String getLabelOverride(String actionId)
    {
        if (actionId == null) return null;
        return this.labelOverrides.get(actionId);
    }

    public void setLabelOverride(String actionId, String labelOrNull)
    {
        if (actionId == null) return;
        String k = actionId.trim();
        if (k.isEmpty()) return;
        if (labelOrNull == null || labelOrNull.trim().isEmpty())
        {
            this.labelOverrides.remove(k);
            return;
        }
        this.labelOverrides.put(k, labelOrNull.trim());
    }

    public String getIconItemIdForAction(String actionId)
    {
        if (actionId == null) return null;
        String fromConfig = this.iconItemIds.get(actionId);
        if (fromConfig != null && fromConfig.isEmpty() == false) return fromConfig;
        String d = HotkeyWheelDefaultIcons.itemIdForAction(actionId);
        return d != null ? d : "minecraft:cobblestone";
    }

    public void setIconIdForAction(String actionId, String iconIdOrNull)
    {
        if (actionId == null) return;
        if (iconIdOrNull == null || iconIdOrNull.trim().isEmpty())
        {
            this.iconItemIds.remove(actionId);
            return;
        }
        this.iconItemIds.put(actionId, iconIdOrNull.trim());
    }

    public boolean isHiddenFromWheel(String actionId)
    {
        if (actionId == null) return false;
        return this.hiddenFromWheel.contains(actionId);
    }

    public List<String> getWheelActionSortOrder()
    {
        return this.wheelActionSortOrder;
    }

    public void setWheelActionSortOrder(List<String> order)
    {
        this.wheelActionSortOrder.clear();
        if (order == null || order.isEmpty()) return;
        for (String s : order)
        {
            if (s != null && s.trim().isEmpty() == false) this.wheelActionSortOrder.add(s.trim());
        }
    }

    public int getShortLabelMaxChars()
    {
        return this.shortLabelMaxChars;
    }

    public boolean wheelShowDividers() { return this.wheelShowDividers; }
    public float wheelInnerRingAlpha() { return this.wheelInnerRingAlpha; }
    public float wheelOuterRingAlpha() { return this.wheelOuterRingAlpha; }
    public int wheelHoverTintColor() { return this.wheelHoverTintColor; }
    public float wheelIconScaleInner() { return this.wheelIconScaleInner; }
    public float wheelIconScaleOuter() { return this.wheelIconScaleOuter; }
    public float wheelRingGapPx() { return this.wheelRingGapPx; }
    public String wheelTheme() { return this.wheelTheme; }
    public boolean wheelDebugLogging() { return this.wheelDebugLogging; }

    public void setWheelTheme(String theme)
    {
        if (theme == null) return;
        String t = theme.trim();
        if (t.isEmpty()) return;
        this.wheelTheme = t;
    }

    public void sortActions(List<WheelAction> inOut)
    {
        if (inOut == null || inOut.size() < 2) return;
        if (this.wheelActionSortOrder.isEmpty()) return;
        inOut.sort(Comparator
                .comparingInt((WheelAction a) -> {
                    int i = this.wheelActionSortOrder.indexOf(a.getActionId());
                    return i < 0 ? 1_000_000 + Math.abs(a.getActionId().hashCode() % 100_000) : i;
                })
                .thenComparing(a -> a.getActionId(), String.CASE_INSENSITIVE_ORDER));
    }

    public boolean isActionExcludedFromWheel(String comboIdUpper, String keyTranslationKey)
    {
        if (keyTranslationKey == null) return false;
        String a = (comboIdUpper != null ? comboIdUpper : "").toUpperCase(Locale.ROOT) + "|" + keyTranslationKey;
        return this.disabledOnCombo.contains(a);
    }

    public void setActionParticipation(String comboIdUpper, String keyTranslationKey, boolean participateInWheel)
    {
        if (keyTranslationKey == null) return;
        String u = comboIdUpper != null ? comboIdUpper.toUpperCase(Locale.ROOT) : "";
        String id = u + "|" + keyTranslationKey;
        this.disabledOnCombo.removeIf(s -> s.equals(id));
        if (participateInWheel == false) this.disabledOnCombo.add(id);
    }

    public void toggleActionExcludedForWheel(String comboIdUpper, String keyTranslationKey)
    {
        if (keyTranslationKey == null) return;
        String u = comboIdUpper != null ? comboIdUpper.toUpperCase(Locale.ROOT) : "";
        String id = u + "|" + keyTranslationKey;
        if (this.disabledOnCombo.removeIf(s -> s.equals(id)) == false) this.disabledOnCombo.add(id);
    }

    public void load()
    {
        this.enabledCombos.clear();
        this.disabledOnCombo.clear();
        this.labelOverrides.clear();
        this.iconItemIds.clear();
        this.hiddenFromWheel.clear();
        this.wheelActionSortOrder.clear();
        this.shortLabelMaxChars = 20;
        this.wheelShowDividers = true;
        this.wheelInnerRingAlpha = 0.72f;
        this.wheelOuterRingAlpha = 0.78f;
        this.wheelHoverTintColor = 0xFFFFFFFF;
        this.wheelIconScaleInner = 1.10f;
        this.wheelIconScaleOuter = 1.18f;
        this.wheelRingGapPx = 10f;
        this.wheelTheme = "tactical";
        this.wheelDebugLogging = false;
        if (Files.isRegularFile(this.file) == false)
        {
            this.migrateFromMalilib();
            if (Files.isRegularFile(this.file) == false) this.save();
        }
        if (Files.isRegularFile(this.file) == false) return;
        try (Reader r = Files.newBufferedReader(this.file)) { this.parse(r); }
        catch (JsonSyntaxException e)
        {
            HotkeyWheelClient.LOGGER.warn("Hotkey Wheel: invalid config JSON '{}'", this.file, e);
            this.resetToDefaultConfigAndSave();
        }
        catch (JsonParseException e)
        {
            HotkeyWheelClient.LOGGER.warn("Hotkey Wheel: failed to parse config '{}'", this.file, e);
            this.resetToDefaultConfigAndSave();
        }
        catch (IOException e)
        {
            HotkeyWheelClient.LOGGER.warn("Hotkey Wheel: failed to read config '{}'", this.file, e);
        }

        // Migration: older configs may have persisted the previous default icon scales,
        // which would "override" new defaults and make UI changes appear to have no effect.
        boolean migrated = false;
        if (approx(this.wheelIconScaleInner, 0.88f) || approx(this.wheelIconScaleInner, 0.96f))
        {
            this.wheelIconScaleInner = 1.10f;
            migrated = true;
        }
        if (approx(this.wheelIconScaleOuter, 1.08f))
        {
            this.wheelIconScaleOuter = 1.18f;
            migrated = true;
        }
        if (migrated) this.save();
    }

    private void resetToDefaultConfigAndSave()
    {
        this.enabledCombos.clear();
        this.disabledOnCombo.clear();
        this.labelOverrides.clear();
        this.iconItemIds.clear();
        this.hiddenFromWheel.clear();
        this.wheelActionSortOrder.clear();
        this.shortLabelMaxChars = 20;
        this.wheelShowDividers = true;
        this.wheelInnerRingAlpha = 0.72f;
        this.wheelOuterRingAlpha = 0.78f;
        this.wheelHoverTintColor = 0xFFFFFFFF;
        this.wheelIconScaleInner = 1.10f;
        this.wheelIconScaleOuter = 1.18f;
        this.wheelRingGapPx = 10f;
        this.wheelTheme = "tactical";
        this.wheelDebugLogging = false;
        this.save();
    }

    public void save()
    {
        try
        {
            Files.createDirectories(this.file.getParent());
            JsonObject root = new JsonObject();
            JsonObject g = new JsonObject();
            g.add("hotkeyWheelEnabledCombos", toJsonArray(new ArrayList<>(this.enabledCombos)));
            g.add("hotkeyWheelDisabledPerCombo", toJsonArray(this.disabledOnCombo));
            g.add("labelOverrides", toJsonStringMap(this.labelOverrides));
            g.add("iconItemIds", toJsonStringMap(this.iconItemIds));
            g.add("hiddenFromWheel", toJsonArray(new ArrayList<>(this.hiddenFromWheel)));
            g.add("wheelActionSortOrder", toJsonArray(this.wheelActionSortOrder));
            g.addProperty("shortLabelMaxChars", this.shortLabelMaxChars);
            JsonObject ui = new JsonObject();
            ui.addProperty("showDividers", this.wheelShowDividers);
            ui.addProperty("innerRingAlpha", this.wheelInnerRingAlpha);
            ui.addProperty("outerRingAlpha", this.wheelOuterRingAlpha);
            ui.addProperty("hoverTintColor", this.wheelHoverTintColor);
            ui.addProperty("iconScaleInner", this.wheelIconScaleInner);
            ui.addProperty("iconScaleOuter", this.wheelIconScaleOuter);
            ui.addProperty("ringGapPx", this.wheelRingGapPx);
            ui.addProperty("theme", this.wheelTheme);
            ui.addProperty("debugLogging", this.wheelDebugLogging);
            g.add("ui", ui);
            root.add("Generic", g);
            Files.writeString(this.file, GSON.toJson(root));
        }
        catch (IOException e)
        {
            HotkeyWheelClient.LOGGER.warn("Hotkey Wheel: failed to save config '{}'", this.file, e);
        }
    }

    private void migrateFromMalilib()
    {
        Path dir = this.file.getParent();
        if (dir == null) return;
        Path malilib = dir.resolve("malilib.json");
        if (Files.isRegularFile(malilib) == false) return;
        try (Reader r = Files.newBufferedReader(malilib))
        {
            JsonElement el = GSON.fromJson(r, JsonElement.class);
            if (el == null || el.isJsonObject() == false) return;
            JsonObject root = el.getAsJsonObject();
            if (root.has("Generic") == false || root.get("Generic").isJsonObject() == false) return;
            JsonObject m = root.getAsJsonObject("Generic");
            JsonObject out = new JsonObject();
            JsonObject outG = new JsonObject();
            if (m.has("hotkeyWheelWhitelist")) outG.add("hotkeyWheelWhitelist", m.get("hotkeyWheelWhitelist").deepCopy());
            if (m.has("hotkeyWheelDisabledPerCombo")) outG.add("hotkeyWheelDisabledPerCombo", m.get("hotkeyWheelDisabledPerCombo").deepCopy());
            if (outG.size() == 0) return;
            out.add("Generic", outG);
            Files.writeString(this.file, GSON.toJson(out));
        }
        catch (IOException e) { HotkeyWheelClient.LOGGER.warn("Hotkey Wheel: failed to save config '{}'", this.file, e); }
    }

    private void parse(Reader r) throws JsonParseException
    {
        JsonObject root = GSON.fromJson(r, JsonObject.class);
        if (root == null || root.has("Generic") == false || root.get("Generic").isJsonObject() == false)
        {
            throw new JsonParseException("hotkeywheel.json: missing Generic object");
        }
        JsonObject g = root.getAsJsonObject("Generic");
        readStringSet(g, "hotkeyWheelEnabledCombos", this.enabledCombos);
        if (this.enabledCombos.isEmpty()) readStringSetAsWhitelist(g, "hotkeyWheelWhitelist", this.enabledCombos);
        readStringArray(g, "hotkeyWheelDisabledPerCombo", this.disabledOnCombo);
        readStringStringMap(g, "labelOverrides", this.labelOverrides);
        readStringStringMap(g, "iconItemIds", this.iconItemIds);
        readStringSetCaseSensitive(g, "hiddenFromWheel", this.hiddenFromWheel);
        readStringList(g, "wheelActionSortOrder", this.wheelActionSortOrder);
        if (g != null && g.has("shortLabelMaxChars") && g.get("shortLabelMaxChars").isJsonPrimitive())
        {
            try { this.shortLabelMaxChars = g.get("shortLabelMaxChars").getAsInt(); } catch (Exception ignored) { }
            if (this.shortLabelMaxChars < 4) this.shortLabelMaxChars = 4;
            if (this.shortLabelMaxChars > 64) this.shortLabelMaxChars = 64;
        }
        if (g != null && g.has("ui") && g.get("ui").isJsonObject())
        {
            JsonObject ui = g.getAsJsonObject("ui");
            if (ui.has("showDividers") && ui.get("showDividers").isJsonPrimitive())
            {
                try { this.wheelShowDividers = ui.get("showDividers").getAsBoolean(); } catch (Exception ignored) { }
            }
            if (ui.has("innerRingAlpha") && ui.get("innerRingAlpha").isJsonPrimitive())
            {
                try { this.wheelInnerRingAlpha = clamp01(ui.get("innerRingAlpha").getAsFloat()); } catch (Exception ignored) { }
            }
            if (ui.has("outerRingAlpha") && ui.get("outerRingAlpha").isJsonPrimitive())
            {
                try { this.wheelOuterRingAlpha = clamp01(ui.get("outerRingAlpha").getAsFloat()); } catch (Exception ignored) { }
            }
            if (ui.has("hoverTintColor") && ui.get("hoverTintColor").isJsonPrimitive())
            {
                try { this.wheelHoverTintColor = ui.get("hoverTintColor").getAsInt(); } catch (Exception ignored) { }
            }
            if (ui.has("iconScaleInner") && ui.get("iconScaleInner").isJsonPrimitive())
            {
                try { this.wheelIconScaleInner = clamp(ui.get("iconScaleInner").getAsFloat(), 0.5f, 2.0f); } catch (Exception ignored) { }
            }
            if (ui.has("iconScaleOuter") && ui.get("iconScaleOuter").isJsonPrimitive())
            {
                try { this.wheelIconScaleOuter = clamp(ui.get("iconScaleOuter").getAsFloat(), 0.5f, 2.0f); } catch (Exception ignored) { }
            }
            if (ui.has("ringGapPx") && ui.get("ringGapPx").isJsonPrimitive())
            {
                try { this.wheelRingGapPx = clamp(ui.get("ringGapPx").getAsFloat(), 0f, 48f); } catch (Exception ignored) { }
            }
            if (ui.has("theme") && ui.get("theme").isJsonPrimitive())
            {
                try
                {
                    String t = ui.get("theme").getAsString();
                    if (t != null && t.trim().isEmpty() == false) this.wheelTheme = t.trim();
                }
                catch (Exception ignored) { }
            }
            if (ui.has("debugLogging") && ui.get("debugLogging").isJsonPrimitive())
            {
                try { this.wheelDebugLogging = ui.get("debugLogging").getAsBoolean(); } catch (Exception ignored) { }
            }
        }
    }

    private static float clamp01(float v)
    {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static float clamp(float v, float min, float max)
    {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static boolean approx(float a, float b)
    {
        return Math.abs(a - b) < 0.0005f;
    }

    private static void readStringSetCaseSensitive(JsonObject g, String key, Set<String> out)
    {
        if (g == null || g.has(key) == false || g.get(key).isJsonArray() == false) return;
        JsonArray a = g.getAsJsonArray(key);
        for (JsonElement e : a)
        {
            if (e != null && e.isJsonPrimitive())
            {
                String s = e.getAsString();
                if (s != null && s.trim().isEmpty() == false) out.add(s.trim());
            }
        }
    }

    private static void readStringSet(JsonObject g, String key, Set<String> out)
    {
        if (g == null || g.has(key) == false || g.get(key).isJsonArray() == false) return;
        JsonArray a = g.getAsJsonArray(key);
        for (JsonElement e : a)
        {
            if (e != null && e.isJsonPrimitive())
            {
                String s = e.getAsString();
                if (s != null && s.trim().isEmpty() == false) out.add(s.trim().toUpperCase(Locale.ROOT));
            }
        }
    }

    private static void readStringSetAsWhitelist(JsonObject g, String key, Set<String> out)
    {
        if (g == null || g.has(key) == false || g.get(key).isJsonArray() == false) return;
        JsonArray a = g.getAsJsonArray(key);
        for (JsonElement e : a)
        {
            if (e != null && e.isJsonPrimitive())
            {
                String s = e.getAsString();
                if (s != null && s.trim().isEmpty() == false) out.add(s.trim().toUpperCase(Locale.ROOT));
            }
        }
    }

    private static void readStringArray(JsonObject g, String key, List<String> out)
    {
        if (g == null || g.has(key) == false || g.get(key).isJsonArray() == false) return;
        JsonArray a = g.getAsJsonArray(key);
        for (JsonElement e : a)
        {
            if (e != null && e.isJsonPrimitive())
            {
                String s = e.getAsString();
                if (s != null && s.trim().isEmpty() == false) out.add(s.trim());
            }
        }
    }

    private static JsonArray toJsonArray(List<String> in)
    {
        JsonArray a = new JsonArray();
        for (String s : in)
        {
            if (s != null && s.trim().isEmpty() == false) a.add(s.trim());
        }
        return a;
    }

    private static JsonObject toJsonStringMap(Map<String, String> map)
    {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, String> e : map.entrySet())
        {
            if (e.getKey() != null && e.getValue() != null) o.addProperty(e.getKey(), e.getValue());
        }
        return o;
    }

    private static void readStringStringMap(JsonObject g, String key, Map<String, String> out)
    {
        if (g == null || g.has(key) == false || g.get(key).isJsonObject() == false) return;
        JsonObject o = g.getAsJsonObject(key);
        for (Map.Entry<String, com.google.gson.JsonElement> e : o.entrySet())
        {
            if (e.getValue() != null && e.getValue().isJsonPrimitive()) out.put(e.getKey(), e.getValue().getAsString());
        }
    }

    private static void readStringList(JsonObject g, String key, List<String> out)
    {
        if (g == null || g.has(key) == false || g.get(key).isJsonArray() == false) return;
        JsonArray a = g.getAsJsonArray(key);
        for (JsonElement e : a)
        {
            if (e != null && e.isJsonPrimitive())
            {
                String s = e.getAsString();
                if (s != null && s.trim().isEmpty() == false) out.add(s.trim());
            }
        }
    }
}
