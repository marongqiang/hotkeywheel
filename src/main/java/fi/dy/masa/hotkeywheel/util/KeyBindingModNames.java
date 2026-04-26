package fi.dy.masa.hotkeywheel.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resource.language.I18n;

/**
 * Heuristic: key.forward → Minecraft; key.tweakeroo.x → tweakeroo when that mod is loaded.
 */
public final class KeyBindingModNames
{
    private KeyBindingModNames() { }

    public static String getModId(String translationKey)
    {
        if (translationKey == null || translationKey.isEmpty()) return "minecraft";
        if (translationKey.startsWith("key.categories.")) return "minecraft";
        if (translationKey.startsWith("key.") == false) return "minecraft";
        int i1 = 4; // "key."
        int i2 = translationKey.indexOf('.', i1);
        if (i2 < 0) return "minecraft";
        String second = translationKey.substring(i1, i2);
        if (second.equals("hotkey") || second.equals("keyboard") || second.equals("mouse") || second.equals("narrator"))
        {
            return "minecraft";
        }
        if (FabricLoader.getInstance().isModLoaded(second)) return second;
        return "minecraft";
    }

    public static String getModDisplayName(String modId)
    {
        if ("minecraft".equals(modId)) return I18n.translate("hotkeywheel.modname.minecraft");
        return FabricLoader.getInstance().getModContainer(modId)
                .map(c -> c.getMetadata().getName())
                .orElse(modId);
    }
}
